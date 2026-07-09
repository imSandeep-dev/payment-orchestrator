package com.payflow.orchestrator.service;

/** The three states of a circuit breaker pattern. */
public enum CircuitState {
    CLOSED, OPEN, HALF_OPEN
}