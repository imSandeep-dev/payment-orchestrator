package com.payflow.orchestrator.gateway;

/**
 * In-code representation of Section B4.3's mock control headers.
 * Today this is constructed directly in tests; starting Day 11-12, the API
 * layer will populate one of these from real incoming HTTP headers
 * (X-Mock-Response, X-Mock-Delay-Ms, X-Mock-Gateway-Down) and pass it down
 * through Controller -> Service -> Router -> Adapter.
 */
public record MockInstruction(MockResponseType responseType, long delayMillis, boolean gatewayDown) {

    public static MockInstruction success() {
        return new MockInstruction(MockResponseType.SUCCESS, 0, false);
    }
}