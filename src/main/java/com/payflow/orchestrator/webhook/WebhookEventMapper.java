package com.payflow.orchestrator.webhook;

import com.payflow.orchestrator.domain.TransactionEvent;
import com.payflow.orchestrator.domain.TransactionState;

import java.util.List;

import static com.payflow.orchestrator.domain.TransactionEvent.*;


public final class WebhookEventMapper {

    private WebhookEventMapper() {
    }

    public static List<TransactionEvent> chainFor(WebhookEventType webhookEvent, TransactionState currentState) {
        return switch (webhookEvent) {
            case PAYMENT_CAPTURED -> chainToCaptured(currentState);
            case PAYMENT_FAILED -> List.of(GATEWAY_AUTH_DECLINE);
            case PAYMENT_REFUNDED -> List.of(GATEWAY_REFUND_SUCCESS);
            case PAYMENT_PARTIALLY_REFUNDED -> List.of(GATEWAY_PARTIAL_REFUND);
            case PAYMENT_SETTLED -> List.of(GATEWAY_SETTLED);
            case DISPUTE_CREATED -> List.of(TransactionEvent.DISPUTE_OPENED);
            // captured/settled transaction later
            // reversed by the gateway must NOT silently disappear or
            // auto-refund — it becomes RECONCILIATION_MISMATCH for human
            case PAYMENT_REVERSED -> List.of(RECONCILIATION_OVERRIDE);
        };
    }

    private static List<TransactionEvent> chainToCaptured(TransactionState currentState) {
        return switch (currentState) {
            case AUTH_INITIATED -> List.of(GATEWAY_AUTH_SUCCESS, CAPTURE_INITIATED, GATEWAY_CAPTURE_SUCCESS);
            case AUTHORISED -> List.of(CAPTURE_INITIATED, GATEWAY_CAPTURE_SUCCESS);
            case CAPTURE_INITIATED -> List.of(GATEWAY_CAPTURE_SUCCESS);
            default -> List.of(GATEWAY_CAPTURE_SUCCESS); // will correctly fail validation if truly invalid
        };
    }
}