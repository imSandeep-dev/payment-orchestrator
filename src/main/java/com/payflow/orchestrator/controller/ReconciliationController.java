package com.payflow.orchestrator.controller;

import com.payflow.orchestrator.domain.ReconciliationLogEntry;
import com.payflow.orchestrator.gateway.MockInstruction;
import com.payflow.orchestrator.service.ReconciliationEngine;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private static final List<String> KNOWN_GATEWAYS = List.of("razorpay", "stripe", "payu", "upi");

    private final ReconciliationEngine engine;

    public ReconciliationController(ReconciliationEngine engine) {
        this.engine = engine;
    }

    public record TriggerResponse(UUID runId) {}
    public record ReconciliationEntryResponse(UUID transactionId, String discrepancyType, String internalState,
                                              String gatewayReportedState, String resolution, boolean requiresManualReview) {
        static ReconciliationEntryResponse from(ReconciliationLogEntry e) {
            return new ReconciliationEntryResponse(e.getTransactionId(), e.getDiscrepancyType(), e.getInternalState(),
                    e.getGatewayReportedState(), e.getResolution(), e.isRequiresManualReview());
        }
    }

    @PostMapping("/trigger")
    public TriggerResponse trigger() {
        UUID runId = UUID.randomUUID();
        Map<String, MockInstruction> defaults = KNOWN_GATEWAYS.stream()
                .collect(java.util.stream.Collectors.toMap(g -> g, g -> MockInstruction.success()));
        engine.runStaleTransactionCheck(runId, defaults);
        engine.runSettlementCheck(runId, defaults);
        return new TriggerResponse(runId);
    }

    @GetMapping("/reports/{runId}")
    public List<ReconciliationEntryResponse> getReport(@PathVariable UUID runId) {
        return engine.getReport(runId).stream().map(ReconciliationEntryResponse::from).toList();
    }
}