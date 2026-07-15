package com.payflow.orchestrator.controller;

import com.payflow.orchestrator.domain.TransactionState;
import com.payflow.orchestrator.repository.TransactionRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private static final List<TransactionState> SUCCESS_STATES =
            List.of(TransactionState.CAPTURED, TransactionState.PARTIALLY_CAPTURED, TransactionState.SETTLED);

    private final TransactionRepository transactionRepository;

    public AnalyticsController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public record SuccessRateResponse(String gateway, long successCount, long totalAttempted, double successRate) {}

    @GetMapping("/success-rate")
    public SuccessRateResponse successRate(@RequestParam String gateway) {
        long success = transactionRepository.countByGatewayAndStateIn(gateway, SUCCESS_STATES);
        long total = transactionRepository.countByGateway(gateway);
        double rate = total == 0 ? 0.0 : (double) success / total;
        return new SuccessRateResponse(gateway, success, total, rate);
    }

    public record VolumeResponse(String gateway, long transactionCount, long totalAmountPaise) {}

    @GetMapping("/volume")
    public VolumeResponse volume(@RequestParam(required = false) String gateway) {
        if (gateway == null) {
            return new VolumeResponse(null, transactionRepository.count(), transactionRepository.sumAllAmounts());
        }
        return new VolumeResponse(gateway, transactionRepository.countByGateway(gateway),
                transactionRepository.sumAmountsByGateway(gateway));
    }
}