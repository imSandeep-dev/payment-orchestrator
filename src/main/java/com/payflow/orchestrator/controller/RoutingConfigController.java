package com.payflow.orchestrator.controller;

import com.payflow.orchestrator.domain.RoutingConfig;
import com.payflow.orchestrator.repository.RoutingConfigRepository;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/routing/config")
public class RoutingConfigController {

    private static final String DEFAULT_CONFIG_KEY = "default";
    private static final BigDecimal WEIGHT_SUM_TOLERANCE = new BigDecimal("0.001");

    private final RoutingConfigRepository repository;

    public RoutingConfigController(RoutingConfigRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public RoutingConfigResponse getConfig() {
        RoutingConfig config = repository.findById(DEFAULT_CONFIG_KEY)
                .orElseThrow(() -> new IllegalStateException("routing_config 'default' row missing — check V11 seed migration"));
        return RoutingConfigResponse.from(config);
    }

    @PutMapping
    @Transactional
    public RoutingConfigResponse updateConfig(@Valid @RequestBody UpdateRoutingConfigRequest request) {
        BigDecimal sum = request.weightSuccessRate().add(request.weightLatency()).add(request.weightCost())
                .add(request.weightHealth()).add(request.weightPaymentMethodFit());
        if (sum.subtract(BigDecimal.ONE).abs().compareTo(WEIGHT_SUM_TOLERANCE) > 0) {
            // Once the global @ControllerAdvice exists
            // map this to a clean 400 PAYMENT_ROUTING_WEIGHTS_INVALID.
            // For now, Spring's default error handler returns 500 for an uncaught
            // IllegalArgumentException — the request IS rejected, just not with the
            // final polished error shape yet.
            throw new IllegalArgumentException(
                    "Routing weights must sum to 1.0 (got %s) — mirrors the DB-level weights_sum_to_one CHECK constraint (Day 2, V1)"
                            .formatted(sum));
        }

        // Fetch-mutate-save on the MANAGED entity — never construct a fresh
        // detached RoutingConfig here, which would silently overwrite created_at.
        RoutingConfig config = repository.findById(DEFAULT_CONFIG_KEY)
                .orElseThrow(() -> new IllegalStateException("routing_config 'default' row missing"));
        config.applyWeights(request.weightSuccessRate(), request.weightLatency(), request.weightCost(),
                request.weightHealth(), request.weightPaymentMethodFit(), request.degradedScoreGapThreshold(),
                request.slidingWindowMinutes());

        return RoutingConfigResponse.from(repository.save(config));
    }
}