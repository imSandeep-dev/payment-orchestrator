package com.payflow.orchestrator.gateway;

/** The five outcomes every gateway operation (authorize/capture/void/refund) can produce. */
public enum GatewayOutcome {
    SUCCESS, DECLINED, TIMEOUT, SERVER_ERROR, RATE_LIMITED
}