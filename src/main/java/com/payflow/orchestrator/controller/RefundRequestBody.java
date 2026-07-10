package com.payflow.orchestrator.controller;

import jakarta.validation.constraints.Positive;

public record RefundRequestBody(@Positive long amountPaise, String reason) {}