package com.payflow.orchestrator.controller;

import jakarta.validation.constraints.Positive;

public record CaptureRequestBody(@Positive long amountPaise) {}