package com.payflow.orchestrator.controller;

import com.payflow.orchestrator.domain.RoutingConfig;

import java.math.BigDecimal;

public record RoutingConfigResponse(
        String configKey, BigDecimal weightSuccessRate, BigDecimal weightLatency, BigDecimal weightCost,
        BigDecimal weightHealth, BigDecimal weightPaymentMethodFit, BigDecimal degradedScoreGapThreshold,
        int slidingWindowMinutes) {

    public static RoutingConfigResponse from(RoutingConfig config) {
        return new RoutingConfigResponse(config.getConfigKey(), config.getWeightSuccessRate(),
                config.getWeightLatency(), config.getWeightCost(), config.getWeightHealth(),
                config.getWeightPaymentMethodFit(), config.getDegradedScoreGapThreshold(),
                config.getSlidingWindowMinutes());
    }
}