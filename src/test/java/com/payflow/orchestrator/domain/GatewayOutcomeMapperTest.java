package com.payflow.orchestrator.domain;

import com.payflow.orchestrator.gateway.GatewayOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayOutcomeMapperTest {

    @Test
    void fullCaptureMapsToCaptureSuccess() {
        assertThat(GatewayOutcomeMapper.forCapture(GatewayOutcome.SUCCESS, 1000L, 1000L))
                .isEqualTo(TransactionEvent.GATEWAY_CAPTURE_SUCCESS);
    }

    @Test
    void partialCaptureMapsToPartialCapture() {
        assertThat(GatewayOutcomeMapper.forCapture(GatewayOutcome.SUCCESS, 400L, 1000L))
                .isEqualTo(TransactionEvent.GATEWAY_PARTIAL_CAPTURE);
    }

    @Test
    void failedCaptureMapsToCaptureError() {
        assertThat(GatewayOutcomeMapper.forCapture(GatewayOutcome.SERVER_ERROR, 1000L, 1000L))
                .isEqualTo(TransactionEvent.GATEWAY_CAPTURE_ERROR);
    }

    @Test
    void successfulVoidMapsCorrectly() {
        assertThat(GatewayOutcomeMapper.forVoid(GatewayOutcome.SUCCESS)).isEqualTo(TransactionEvent.GATEWAY_VOID_SUCCESS);
    }

    @Test
    void failedVoidThrows_perKnownGapInADR007() {
        assertThatThrownBy(() -> GatewayOutcomeMapper.forVoid(GatewayOutcome.SERVER_ERROR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-007");
    }

    @Test
    void partialRefundMapsToPartialRefund() {
        assertThat(GatewayOutcomeMapper.forRefund(GatewayOutcome.SUCCESS, 500L, 1000L))
                .isEqualTo(TransactionEvent.GATEWAY_PARTIAL_REFUND);
    }
}