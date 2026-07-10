package com.payflow.orchestrator.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.payflow.orchestrator.domain.Transaction;
import com.payflow.orchestrator.gateway.MockInstruction;
import com.payflow.orchestrator.gateway.MockResponseType;
import com.payflow.orchestrator.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final List<String> KNOWN_GATEWAYS = List.of("razorpay", "stripe", "payu", "upi");

    private final TransactionService transactionService;

    public PaymentController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public TransactionResponse initiate(@Valid @RequestBody InitiatePaymentRequest request,
                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                        @RequestHeader(value = "X-Mock-Response", required = false) String mockResponse,
                                        @RequestHeader(value = "X-Mock-Delay-Ms", required = false, defaultValue = "0") long delayMs,
                                        @RequestHeader(value = "X-Mock-Gateway-Down", required = false, defaultValue = "false") boolean gatewayDown) {
        MockInstruction instruction = buildMockInstruction(mockResponse, delayMs, gatewayDown);
        // Headers describe ONE mock signal per request, applying
        // to whichever gateway the router actually selects — not a per-gateway
        // map. We translate that into a uniform map across all known gateways,
        // so GatewayFailoverExecutor's per-gateway lookup (Day 8) still works
        // unmodified regardless of which gateway wins routing.
        Map<String, MockInstruction> uniform = KNOWN_GATEWAYS.stream()
                .collect(Collectors.toMap(g -> g, g -> instruction));

        Transaction txn = transactionService.initiatePayment(request.merchantId(), request.merchantOrderId(),
                request.amountPaise(), request.currency(), request.paymentMethod(), idempotencyKey, uniform);
        return TransactionResponse.from(txn);
    }

    @GetMapping("/{id}")
    public TransactionResponse getById(@PathVariable UUID id) {
        return TransactionResponse.from(transactionService.getById(id));
    }

    @GetMapping
    public TransactionResponse getByMerchantOrderId(@RequestParam UUID merchantId,
                                                    @RequestParam("merchant_order_id") String merchantOrderId) {
        // NOTE: merchant_id is an explicit query param today, not derived from
        // an authenticated API key — real API-key auth middleware
        return TransactionResponse.from(transactionService.getByMerchantOrderId(merchantId, merchantOrderId));
    }

    @PostMapping("/{id}/capture")
    public TransactionResponse capture(@PathVariable UUID id, @Valid @RequestBody CaptureRequestBody body,
                                       @RequestHeader(value = "X-Mock-Response", required = false) String mockResponse) throws JsonProcessingException {
        MockInstruction instruction = buildMockInstruction(mockResponse, 0, false);
        return TransactionResponse.from(transactionService.capture(id, body.amountPaise(), instruction));
    }

    @PostMapping("/{id}/void")
    public TransactionResponse voidPayment(@PathVariable UUID id,
                                           @RequestHeader(value = "X-Mock-Response", required = false) String mockResponse) throws JsonProcessingException {
        MockInstruction instruction = buildMockInstruction(mockResponse, 0, false);
        return TransactionResponse.from(transactionService.voidAuthorization(id, instruction));
    }

    @PostMapping("/{id}/refund")
    public RefundResponse refund(@PathVariable UUID id, @Valid @RequestBody RefundRequestBody body,
                                 @RequestHeader(value = "X-Mock-Response", required = false) String mockResponse) throws JsonProcessingException {
        MockInstruction instruction = buildMockInstruction(mockResponse, 0, false);
        return RefundResponse.from(transactionService.refund(id, body.amountPaise(), body.reason(), instruction));
    }

    @GetMapping("/{id}/refunds")
    public List<RefundResponse> getRefunds(@PathVariable UUID id) {
        return transactionService.getRefunds(id).stream().map(RefundResponse::from).toList();
    }

    @GetMapping("/{id}/timeline")
    public List<TimelineEntryResponse> getTimeline(@PathVariable UUID id) {
        return transactionService.getTimeline(id).stream().map(TimelineEntryResponse::from).toList();
    }

    private MockInstruction buildMockInstruction(String mockResponse, long delayMs, boolean gatewayDown) {
        MockResponseType type = mockResponse == null
                ? MockResponseType.SUCCESS
                : MockResponseType.valueOf(mockResponse.toUpperCase().replace('-', '_'));
        return new MockInstruction(type, delayMs, gatewayDown);
    }
}