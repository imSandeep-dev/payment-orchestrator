package com.payflow.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.payflow.orchestrator.domain.*;
import com.payflow.orchestrator.exception.ApiException;
import com.payflow.orchestrator.gateway.*;
import com.payflow.orchestrator.repository.*;
import com.payflow.orchestrator.util.GatewayResponseSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AUDIT LOG GRANULARITY (see ADR-007): one row per REAL outbound gateway
 * call during authorization, not per internal FSM micro-transition. The
 * executor internally walks ROUTE_SELECTED -> AUTH_INITIATED -> <outcome>
 * (and FAILOVER back to ROUTE_SELECTED between attempts) purely to satisfy
 * the state machine's transition guards — those intermediate steps aren't
 * independently meaningful audit events the way webhook catch-up
 * chain steps are (each of THOSE represents genuinely separate information
 * arriving asynchronously). Every attempt genuinely starts from
 * ROUTE_SELECTED (that's what FAILOVER always returns to), so logging
 * fromState=ROUTE_SELECTED for each attempt row is accurate, not a shortcut.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionStateLogRepository stateLogRepository;
    private final RefundRepository refundRepository;
    private final GatewayConfigRepository gatewayConfigRepository;
    private final IdempotencyService idempotencyService;
    private final GatewayFailoverExecutor failoverExecutor;
    private final TransactionStateMachine stateMachine;
    private final GatewayResponseSanitizer sanitizer;
    private final Map<String, PaymentGateway> gatewaysByName;
    private final CaptureRetryExecutor captureRetryExecutor;

    public TransactionService(TransactionRepository transactionRepository,
                              TransactionStateLogRepository stateLogRepository,
                              RefundRepository refundRepository,
                              GatewayConfigRepository gatewayConfigRepository,
                              IdempotencyService idempotencyService,
                              GatewayFailoverExecutor failoverExecutor,
                              TransactionStateMachine stateMachine,
                              GatewayResponseSanitizer sanitizer,
                              List<PaymentGateway> gateways, CaptureRetryExecutor captureRetryExecutor, VoidLifecycleRecorder voidLifecycleRecorder) {
        this.transactionRepository = transactionRepository;
        this.stateLogRepository = stateLogRepository;
        this.refundRepository = refundRepository;
        this.gatewayConfigRepository = gatewayConfigRepository;
        this.idempotencyService = idempotencyService;
        this.failoverExecutor = failoverExecutor;
        this.stateMachine = stateMachine;
        this.sanitizer = sanitizer;
        this.gatewaysByName = gateways.stream().collect(Collectors.toMap(PaymentGateway::getGatewayName, g -> g));
        this.captureRetryExecutor = captureRetryExecutor;
        this.voidLifecycleRecorder = voidLifecycleRecorder;
    }

    @Transactional
    public Transaction initiatePayment(UUID merchantId, String merchantOrderId, long amountPaise,
                                       String currency, PaymentMethod paymentMethod, String idempotencyKey,
                                       Map<String, MockInstruction> mockInstructionsByGateway) {
        String requestHash = sha256Hex(merchantOrderId + "|" + amountPaise + "|" + currency + "|" + paymentMethod);
        IdempotencyOutcome idem = idempotencyService.checkAndLock(merchantId, idempotencyKey, requestHash);

        switch (idem.status()) {
            case CONFLICT_IN_FLIGHT -> throw ApiException.idempotencyConflict();
            case KEY_REUSED_DIFFERENT_PAYLOAD -> throw ApiException.idempotencyKeyReused();
            case CACHED_RESPONSE -> {
                return transactionRepository.findById(idem.cachedTransactionId())
                        .orElseThrow(() -> ApiException.transactionNotFound(idem.cachedTransactionId()));
            }
            case PROCEED -> { /* fall through below */ }
            default -> throw new IllegalStateException("Unhandled IdempotencyOutcome.Status: " + idem.status());
        }

        Transaction txn = Transaction.create(merchantId, merchantOrderId, amountPaise, currency, paymentMethod, UUID.randomUUID());
        txn.assignIdempotencyKey(idempotencyKey);
        transactionRepository.saveAndFlush(txn);
        logTransition(txn, null, TransactionState.CREATED, "PAYMENT_REQUEST_RECEIVED", null, null);

        try {
            TransactionState routed = stateMachine.transition(TransactionState.CREATED, TransactionEvent.ROUTE_SELECTED);
            txn.applyState(routed);
            logTransition(txn, TransactionState.CREATED, routed, "ROUTE_SELECTED", null, null);

            FailoverResult result = failoverExecutor.authorizeWithFailover(
                    txn.getId(), paymentMethod.name(), amountPaise, txn.getTraceId(), mockInstructionsByGateway);

            for (AttemptOutcome attempt : result.attempts()) {
                logTransition(txn, TransactionState.ROUTE_SELECTED, attempt.resultingState(),
                        "AUTHORIZE_ATTEMPT:" + attempt.gateway() + ":" + attempt.outcome(),
                        attempt.outcome() == GatewayOutcome.SUCCESS ? result.gatewayReference() : null,
                        attempt.rawResponseJson());
            }

            txn.applyState(result.finalState());
            if (result.isSuccess()) {
                txn.assignGateway(result.successfulGateway(), result.gatewayReference());
            }
            transactionRepository.save(txn);

            idempotencyService.markCompleted(merchantId, idempotencyKey, 200,
                    "{\"transactionId\":\"%s\",\"state\":\"%s\"}".formatted(txn.getId(), result.finalState()), txn.getId());
            return txn;
        } catch (RuntimeException e) {
            // A genuine processing failure (not a business decline) — allows retry per A4.1.
            idempotencyService.markFailed(merchantId, idempotencyKey);
            throw e;
        }
    }

    public Transaction getById(UUID id) {
        return transactionRepository.findById(id).orElseThrow(() -> ApiException.transactionNotFound(id));
    }

    public Transaction getByMerchantOrderId(UUID merchantId, String merchantOrderId) {
        return transactionRepository.findByMerchantIdAndMerchantOrderId(merchantId, merchantOrderId)
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "TRANSACTION_NOT_FOUND", "No transaction found for merchant_order_id " + merchantOrderId, Map.of()));
    }

    public List<TransactionStateLog> getTimeline(UUID transactionId) {
        return stateLogRepository.findByTransactionIdOrderByCreatedAtAsc(transactionId);
    }

    public List<Refund> getRefunds(UUID transactionId) {
        return refundRepository.findByTransactionIdOrderByCreatedAtAsc(transactionId);
    }

    @Transactional
    public Transaction capture(UUID transactionId, long amountPaise, MockInstruction mockInstruction) throws JsonProcessingException {
        Transaction txn = getById(transactionId);
        if (txn.getState() != TransactionState.AUTHORISED && txn.getState() != TransactionState.PARTIALLY_CAPTURED) {
            throw ApiException.invalidTransactionState(txn.getState().name(), "capture");
        }
        long remaining = txn.getAmountPaise() - txn.getCapturedAmountPaise();
        if (amountPaise <= 0 || amountPaise > remaining) {
            throw ApiException.amountExceedsAvailable(amountPaise, remaining, "capture");
        }

        TransactionState initiated = stateMachine.transition(txn.getState(), TransactionEvent.CAPTURE_INITIATED);
        txn.applyState(initiated);
        logTransition(txn, txn.getState(), initiated, "CAPTURE_INITIATED", txn.getGatewayReference(), null);

        PaymentGateway gateway = gatewaysByName.get(txn.getGateway());
        GatewayResult result = captureRetryExecutor.captureWithRetry(
                txn.getGateway(), txn.getId(), txn.getGatewayReference(), amountPaise, txn.getTraceId(),
                mockInstruction, mockInstruction);

        TransactionEvent event = GatewayOutcomeMapper.forCapture(result.outcome(), amountPaise, remaining);
        TransactionState next = stateMachine.transition(initiated, event);
        txn.applyState(next);
        logTransition(txn, initiated, next, event.name(), txn.getGatewayReference(), sanitizer.sanitize(result.rawResponseJson()));

        if (result.isSuccess()) {
            txn.recordCapture(amountPaise);
        }
        return transactionRepository.save(txn);
    }

    // add to constructor params and field:
    private final VoidLifecycleRecorder voidLifecycleRecorder;
// ...assign in constructor as usual...

    // REPLACE the old @Transactional voidAuthorization(...) with:
    public Transaction voidAuthorization(UUID transactionId, MockInstruction mockInstruction) throws JsonProcessingException {
        Transaction txn = getById(transactionId);
        if (txn.getState() != TransactionState.AUTHORISED && txn.getState() != TransactionState.CAPTURE_FAILED) {
            throw ApiException.invalidTransactionState(txn.getState().name(), "void");
        }

        TransactionState initiated = voidLifecycleRecorder.recordVoidInitiated(transactionId);

        PaymentGateway gateway = gatewaysByName.get(txn.getGateway());
        GatewayResult result = gateway.voidAuthorization(new VoidRequest(
                transactionId, txn.getGatewayReference(), txn.getTraceId(), mockInstruction));

        if (!result.isSuccess()) {
            voidLifecycleRecorder.recordVoidFailure(transactionId, initiated, sanitizer.sanitize(result.rawResponseJson()));
            throw ApiException.gatewayOperationFailed("void", result.errorMessage());
        }

        return voidLifecycleRecorder.recordVoidSuccess(transactionId, initiated, sanitizer.sanitize(result.rawResponseJson()));
    }

    @Transactional
    public Refund refund(UUID transactionId, long amountPaise, String reason, MockInstruction mockInstruction) throws JsonProcessingException {
        Transaction txn = getById(transactionId);
        if (txn.getState() != TransactionState.CAPTURED && txn.getState() != TransactionState.PARTIALLY_CAPTURED
                && txn.getState() != TransactionState.SETTLED) {
            throw ApiException.invalidTransactionState(txn.getState().name(), "refund");
        }

        long remaining = txn.getCapturedAmountPaise() - txn.getRefundedAmountPaise();
        if (amountPaise <= 0 || amountPaise > remaining) {
            throw ApiException.amountExceedsAvailable(amountPaise, remaining, "refund");
        }

        GatewayConfig gatewayConfig = gatewayConfigRepository.findById(txn.getGateway())
                .orElseThrow(() -> new IllegalStateException("Unknown gateway: " + txn.getGateway()));
        boolean isPartial = amountPaise < remaining;
        if (isPartial && !gatewayConfig.isSupportsPartialRefund()) {
            throw ApiException.partialRefundNotSupported(txn.getGateway());
        }

        Refund refund = Refund.create(transactionId, amountPaise, reason, "api");
        refundRepository.saveAndFlush(refund);

        TransactionState initiated = stateMachine.transition(txn.getState(), TransactionEvent.REFUND_INITIATED);
        txn.applyState(initiated);
        logTransition(txn, txn.getState(), initiated, "REFUND_INITIATED", txn.getGatewayReference(), null);

        PaymentGateway gateway = gatewaysByName.get(txn.getGateway());
        GatewayResult result = gateway.refund(new RefundRequest(
                transactionId, txn.getGatewayReference(), amountPaise, txn.getTraceId(), mockInstruction));

        TransactionEvent event = GatewayOutcomeMapper.forRefund(result.outcome(), amountPaise, remaining);
        TransactionState next = stateMachine.transition(initiated, event);
        txn.applyState(next);
        logTransition(txn, initiated, next, event.name(), txn.getGatewayReference(), sanitizer.sanitize(result.rawResponseJson()));

        refund.markResult(next.name(), result.gatewayReference());
        if (result.isSuccess()) {
            txn.recordRefund(amountPaise);
        }
        transactionRepository.save(txn);
        return refundRepository.save(refund);
    }

    private void logTransition(Transaction txn, TransactionState from, TransactionState to, String event,
                               String gatewayReference, String sanitizedResponse) {
        stateLogRepository.save(TransactionStateLog.record(txn.getId(), from, to, event, gatewayReference,
                sanitizedResponse, null, txn.getTraceId(), "transaction_service"));
    }

    private String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}