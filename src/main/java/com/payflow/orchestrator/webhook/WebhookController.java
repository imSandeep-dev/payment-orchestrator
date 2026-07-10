package com.payflow.orchestrator.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Map<String, String> SIGNATURE_HEADER_BY_GATEWAY = Map.of(
            "razorpay", "X-Razorpay-Signature",
            "stripe", "Stripe-Signature",
            "payu", "X-PayU-Signature",
            "upi", "X-UPI-Signature"
    );

    private final WebhookEventProcessor processor;
    private final JsonMapper jsonMapper;

    public WebhookController(WebhookEventProcessor processor, JsonMapper jsonMapper) {
        this.processor = processor;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping("/{gateway}")
    public ResponseEntity<Map<String, String>> receive(@PathVariable String gateway,
                                                       @RequestBody String rawBody,
                                                       @RequestHeader Map<String, String> headers) throws JsonProcessingException {
        String signatureHeaderName = SIGNATURE_HEADER_BY_GATEWAY.get(gateway);
        String signature = signatureHeaderName == null ? null : headers.get(signatureHeaderName.toLowerCase());

        WebhookPayloadEnvelope payload = jsonMapper.readValue(rawBody, WebhookPayloadEnvelope.class);
        WebhookEventType eventType;
        try {
            eventType = WebhookEventType.valueOf(payload.eventType());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Unknown event_type: " + payload.eventType()));
        }

        IncomingWebhookRequest request = new IncomingWebhookRequest(gateway, payload.eventId(), eventType,
                payload.gatewayReference(), payload.amountPaise(), payload.currency(), rawBody, signature);

        WebhookProcessingResult result = processor.process(request);
        HttpStatus status = switch (result.outcome()) {
            case PROCESSED, DUPLICATE_ACKNOWLEDGED -> HttpStatus.OK;
            case SIGNATURE_INVALID -> HttpStatus.UNAUTHORIZED;
            case TRANSACTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case AMOUNT_MISMATCH, CURRENCY_MISMATCH -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INVALID_STATE_TRANSITION -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(Map.of("message", result.message()));
    }
}