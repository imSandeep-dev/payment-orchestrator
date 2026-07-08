package com.payflow.orchestrator.service;

/** One gateway's full scoring breakdown — every component kept separately for auditability (gateway_routes table, Day 11-12). */
public record GatewayScore(
        String gateway,
        double totalScore,
        double successRateComponent,
        double latencyComponent,
        double costComponent,
        double healthComponent,
        double fitComponent,
        String circuitBreakerState
) {}