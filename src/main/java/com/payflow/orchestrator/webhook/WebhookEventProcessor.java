package com.payflow.orchestrator.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.payflow.orchestrator.domain.*;
import com.payflow.orchestrator.exception.InvalidStateTransitionException;
import com.payflow.orchestrator.repository.TransactionRepository;
import com.payflow.orchestrator.repository.TransactionStateLogRepository;
import com.payflow.orchestrator.service.WebhookDedupService;
import com.payflow.orchestrator.util.GatewayResponseSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class WebhookEventProcessor {

    private final Map<String, WebhookSignatureVerifier> verifiersByGateway;
    private final WebhookDedupService dedupService;
    private final TransactionRepository transactionRepository;
    private final TransactionStateLogRepository stateLogRepository;
    private final TransactionStateMachine stateMachine;
    private final GatewayResponseSanitizer sanitizer;

    public WebhookEventProcessor(List<WebhookSignatureVerifier> verifiers, WebhookDedupService dedupService,
                                 TransactionRepository transactionRepository,
                                 TransactionStateLogRepository stateLogRepository,
                                 TransactionStateMachine stateMachine, GatewayResponseSanitizer sanitizer) {
        this.verifiersByGateway = verifiers.stream()
                .collect(Collectors.toMap(WebhookSignatureVerifier::getGatewayName, v -> v));
        this.dedupService = dedupService;
        this.transactionRepository = transactionRepository;
        this.stateLogRepository = stateLogRepository;
        this.stateMachine = stateMachine;
        this.sanitizer = sanitizer;
    }

    @Transactional
    public WebhookProcessingResult process(IncomingWebhookRequest request) throws JsonProcessingException {
        WebhookSignatureVerifier verifier = verifiersByGateway.get(request.gateway());
        if (verifier == null || !verifier.verify(request.rawBody(), request.signatureHeader())) {
            return new WebhookProcessingResult(WebhookProcessingResult.Outcome.SIGNATURE_INVALID,
                    "Webhook signature verification failed for gateway " + request.gateway());
        }

        String payloadHash = sha256Hex(request.rawBody());

        boolean isNewEvent = dedupService.tryMarkProcessed(
                request.gateway(), request.eventId(), request.eventType().name(), payloadHash);
        if (!isNewEvent) {
            return new WebhookProcessingResult(WebhookProcessingResult.Outcome.DUPLICATE_ACKNOWLEDGED,
                    "Event already processed — acknowledged without reprocessing");
        }

        Optional<Transaction> maybeTransaction = transactionRepository.findByGatewayReference(request.gatewayReference());
        if (maybeTransaction.isEmpty()) {
            return new WebhookProcessingResult(WebhookProcessingResult.Outcome.TRANSACTION_NOT_FOUND,
                    "No transaction found for gateway_reference " + request.gatewayReference());
        }
        Transaction transaction = maybeTransaction.get();

        if (transaction.getAmountPaise() != request.amountPaise()) {
            return new WebhookProcessingResult(WebhookProcessingResult.Outcome.AMOUNT_MISMATCH,
                    "Webhook amount %d does not match transaction amount %d"
                            .formatted(request.amountPaise(), transaction.getAmountPaise()));
        }
        if (!transaction.getCurrency().equals(request.currency())) {
            return new WebhookProcessingResult(WebhookProcessingResult.Outcome.CURRENCY_MISMATCH,
                    "Webhook currency %s does not match transaction currency %s"
                            .formatted(request.currency(), transaction.getCurrency()));
        }

        List<TransactionEvent> chain = WebhookEventMapper.chainFor(request.eventType(), transaction.getState());
        String sanitizedResponse = sanitizer.sanitize(request.rawBody());
        TransactionState state = transaction.getState();

        try {
            for (TransactionEvent event : chain) {
                TransactionState fromState = state;
                state = stateMachine.transition(state, event);
                transaction.applyState(state);
                stateLogRepository.save(TransactionStateLog.record(
                        transaction.getId(), fromState, state, event.name(), request.gatewayReference(),
                        sanitizedResponse, null, transaction.getTraceId(), "webhook_processor"));
            }
        } catch (InvalidStateTransitionException e) {
            // A webhook whose implied transition genuinely doesn't fit the
            // transaction's real current state — rejected gracefully, no
            // corruption, no 500.
            return new WebhookProcessingResult(WebhookProcessingResult.Outcome.INVALID_STATE_TRANSITION, e.getMessage());
        }

        applyCaptureAmountBookkeeping(transaction, request.eventType(), request.amountPaise());
        transactionRepository.save(transaction);
        dedupService.linkToTransaction(request.gateway(), request.eventId(), transaction.getId());

        return new WebhookProcessingResult(WebhookProcessingResult.Outcome.PROCESSED,
                "Applied %d state transition(s), final state %s".formatted(chain.size(), state));
    }

    private void applyCaptureAmountBookkeeping(Transaction transaction, WebhookEventType eventType, long amountPaise) {
        if (eventType == WebhookEventType.PAYMENT_CAPTURED) {
            transaction.recordCapture(amountPaise);
        } else if (eventType == WebhookEventType.PAYMENT_REFUNDED
                || eventType == WebhookEventType.PAYMENT_PARTIALLY_REFUNDED) {
            transaction.recordRefund(amountPaise);
        }
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