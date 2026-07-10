package com.payflow.orchestrator.controller;

import com.payflow.orchestrator.domain.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record InitiatePaymentRequest(
        @NotNull UUID merchantId,
        @NotBlank String merchantOrderId,
        @Positive long amountPaise,
        @NotBlank String currency,
        @NotNull PaymentMethod paymentMethod) {}