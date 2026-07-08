package com.payflow.orchestrator.gateway;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PayUAdapterTest extends PaymentGatewayContractTest {

    @Override
    protected PaymentGateway gateway() {
        return new PayUAdapter();
    }

    @Test
    void captureAlwaysSucceedsImmediately_becausePayUAutoCapturesAtAuthorization() {
        CaptureRequest request = new CaptureRequest(UUID.randomUUID(), "payu_existing_ref",
                50000L, UUID.randomUUID(), new MockInstruction(MockResponseType.SERVER_ERROR, 0, false));

        GatewayResult result = gateway().capture(request);

        // Even though the instruction says SERVER_ERROR, capture() ignores it —
        // a real PayU capture can't fail after the funds already moved at authorize().
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.gatewayReference()).isEqualTo("payu_existing_ref");
    }
}