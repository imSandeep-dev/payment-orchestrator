package com.payflow.orchestrator.gateway;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UPIAdapterTest extends PaymentGatewayContractTest {

    @Override
    protected PaymentGateway gateway() {
        return new UPIAdapter();
    }

    @Override
    protected GatewayOutcome expectedTimeoutOutcome() {
        // FS-12: mandate window expiry, not a network/gateway failure.
        return GatewayOutcome.MANDATE_EXPIRED;
    }

    @Test
    void voidIsNotSupported_becauseUpiSettlesInstantlyWithNoAuthHold() {
        VoidRequest request = new VoidRequest(UUID.randomUUID(), "upi_existing_ref",
                UUID.randomUUID(), MockInstruction.success());

        GatewayResult result = gateway().voidAuthorization(request);

        assertThat(result.outcome()).isEqualTo(GatewayOutcome.NOT_SUPPORTED);
    }

    @Test
    void captureAlwaysSucceedsImmediately_becauseUpiSettlesInstantly() {
        CaptureRequest request = new CaptureRequest(UUID.randomUUID(), "upi_existing_ref",
                25000L, UUID.randomUUID(), MockInstruction.success());

        GatewayResult result = gateway().capture(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.gatewayReference()).isEqualTo("upi_existing_ref");
    }

    @Test
    void timeoutErrorMessageMentionsMandateWindow_perFS12() {
        GatewayResult result = gateway().authorize(new AuthorizationRequest(
                UUID.randomUUID(), 100000L, "INR", "UPI", UUID.randomUUID(),
                new MockInstruction(MockResponseType.TIMEOUT, 0, false)));

        assertThat(result.errorMessage()).containsIgnoringCase("mandate");
    }
}