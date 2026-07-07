package com.payflow.orchestrator.gateway;

import org.springframework.stereotype.Component;

@Component
public class RazorpayAdapter extends AbstractMockPaymentGateway {

    private static final int AUTH_TIMEOUT_SECONDS = 30; // Section A1.3

    @Override
    public String getGatewayName() {
        return "razorpay";
    }

    @Override
    public GatewayResult authorize(AuthorizationRequest request) {
        return simulate(request.mockInstruction(), "authorize");
    }

    @Override
    public GatewayResult capture(CaptureRequest request) {
        return simulate(request.mockInstruction(), "capture");
    }

    @Override
    public GatewayResult voidAuthorization(VoidRequest request) {
        return simulate(request.mockInstruction(), "void");
    }

    @Override
    public GatewayResult refund(RefundRequest request) {
        return simulate(request.mockInstruction(), "refund");
    }

    @Override
    protected int getAuthTimeoutSeconds() {
        return AUTH_TIMEOUT_SECONDS;
    }
}