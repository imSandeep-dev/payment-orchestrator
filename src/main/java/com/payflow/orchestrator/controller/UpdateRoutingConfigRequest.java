package com.payflow.orchestrator.controller;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateRoutingConfigRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal weightSuccessRate,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal weightLatency,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal weightCost,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal weightHealth,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal weightPaymentMethodFit,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal degradedScoreGapThreshold,
        @Min(1) int slidingWindowMinutes) {}