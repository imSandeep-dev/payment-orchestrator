package com.payflow.orchestrator.gateway;

public record StatusCheckRequest(String gatewayReference, String expectedState, MockInstruction mockInstruction) {}