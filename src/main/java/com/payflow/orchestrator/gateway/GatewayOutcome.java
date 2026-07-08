package com.payflow.orchestrator.gateway;

public enum GatewayOutcome {
    SUCCESS, DECLINED, TIMEOUT, SERVER_ERROR, RATE_LIMITED,MANDATE_EXPIRED,NOT_SUPPORTED
}