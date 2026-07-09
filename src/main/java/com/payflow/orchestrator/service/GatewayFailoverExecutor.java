package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.GatewayOutcomeMapper;
import com.payflow.orchestrator.domain.TransactionEvent;
import com.payflow.orchestrator.domain.TransactionState;
import com.payflow.orchestrator.domain.TransactionStateMachine;
import com.payflow.orchestrator.gateway.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Component
public class GatewayFailoverExecutor {

    /** With UPI exclusive to itself, this effectively means "try every eligible non-UPI gateway." */
    private static final int MAX_GATEWAY_ATTEMPTS = 3;

    private final GatewayRouter router;
    private final GatewayHealthMetrics healthMetrics;
    private final Map<String, PaymentGateway> gatewaysByName;
    private final TransactionStateMachine stateMachine;

    public GatewayFailoverExecutor(GatewayRouter router, GatewayHealthMetrics healthMetrics,
                                   List<PaymentGateway> gateways, TransactionStateMachine stateMachine) {
        this.router = router;
        this.healthMetrics = healthMetrics;
        this.gatewaysByName = gateways.stream().collect(Collectors.toMap(PaymentGateway::getGatewayName, g -> g));
        this.stateMachine = stateMachine;
    }

    public FailoverResult authorizeWithFailover(UUID transactionId, String paymentMethod, long amountPaise,
                                                UUID traceId, Map<String, MockInstruction> mockInstructionsByGateway) {
        List<GatewayScore> candidates = router.scoreEligibleGateways(paymentMethod, amountPaise);
        List<AttemptOutcome> attempts = new ArrayList<>();

        if (candidates.isEmpty()) {
            TransactionState routeFailed = stateMachine.transition(TransactionState.ROUTE_SELECTED, TransactionEvent.ROUTE_FAILED);
            TransactionState finalState = stateMachine.transition(routeFailed, TransactionEvent.EXHAUSTED);
            return new FailoverResult(finalState, null, null, attempts);
        }

        TransactionState state = TransactionState.ROUTE_SELECTED;
        int attemptCount = 0;

        for (int i = 0; i < candidates.size() && attemptCount < MAX_GATEWAY_ATTEMPTS; i++) {
            String gatewayName = candidates.get(i).gateway();
            PaymentGateway gateway = gatewaysByName.get(gatewayName);
            if (gateway == null) {
                continue; // configured in DB but no adapter bean registered — skip defensively
            }

            state = stateMachine.transition(state, TransactionEvent.AUTH_INITIATED);
            attemptCount++;

            MockInstruction instruction = mockInstructionsByGateway.getOrDefault(gatewayName, MockInstruction.success());
            long start = System.currentTimeMillis();
            GatewayResult result = gateway.authorize(new AuthorizationRequest(
                    transactionId, amountPaise, "INR", paymentMethod, traceId, instruction));
            long elapsedMs = System.currentTimeMillis() - start;

            boolean success = result.isSuccess();
            healthMetrics.recordOutcome(gatewayName, paymentMethod, success, elapsedMs);

            TransactionEvent event = GatewayOutcomeMapper.forAuthorize(result.outcome());
            state = stateMachine.transition(state, event);
            attempts.add(new AttemptOutcome(gatewayName, result.outcome(), state, elapsedMs));

            if (state == TransactionState.AUTHORISED) {
                return new FailoverResult(state, gatewayName, result.gatewayReference(), attempts);
            }

            if (state == TransactionState.AUTH_TIMEOUT) {
                boolean hasMoreAttempts = (i + 1 < candidates.size()) && (attemptCount < MAX_GATEWAY_ATTEMPTS);
                if (hasMoreAttempts) {
                    state = stateMachine.transition(state, TransactionEvent.FAILOVER); // -> ROUTE_SELECTED
                    continue;
                }
                state = stateMachine.transition(state, TransactionEvent.EXHAUSTED); // -> FAILED
                break;
            }

            break;
        }

        return new FailoverResult(state, null, null, attempts);
    }
}