package com.payflow.orchestrator.service;

import com.payflow.orchestrator.gateway.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureRetryExecutorTest {

    private final CaptureRetryExecutor executor = new CaptureRetryExecutor(
            List.of(new RazorpayAdapter(), new StripeAdapter(), new PayUAdapter(), new UPIAdapter()));

    @Test
    void succeedsImmediatelyOnFirstAttempt_noBackoffPaid() {
        long start = System.currentTimeMillis();

        GatewayResult result = executor.captureWithRetry("razorpay", UUID.randomUUID(), "pay_ref_1",
                50000L, UUID.randomUUID(), MockInstruction.success(), MockInstruction.success());

        assertThat(result.isSuccess()).isTrue();
        assertThat(System.currentTimeMillis() - start).isLessThan(500);
    }

    @Test
    void exhaustsAllRetriesAndReportsFailure_whenStatusCheckAlsoConfirmsFailure() {
        MockInstruction serverError = new MockInstruction(MockResponseType.SERVER_ERROR, 0, false);

        GatewayResult result = executor.captureWithRetry("razorpay", UUID.randomUUID(), "pay_ref_2",
                50000L, UUID.randomUUID(), serverError, serverError);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void lateSuccessPattern_attemptsFailButStatusCheckConfirmsCaptured_perFS04() {
        MockInstruction attemptsFail = new MockInstruction(MockResponseType.SERVER_ERROR, 0, false);
        MockInstruction statusConfirms = MockInstruction.success();

        GatewayResult result = executor.captureWithRetry("razorpay", UUID.randomUUID(), "pay_ref_3",
                50000L, UUID.randomUUID(), attemptsFail, statusConfirms);

        // All 3 attempts genuinely failed, but the post-exhaustion status
        // poll confirms the gateway actually processed it server-side.
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.gatewayReference()).isEqualTo("pay_ref_3");
    }
}