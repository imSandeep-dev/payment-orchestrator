package com.payflow.orchestrator.gateway;

/**
 * Uniform interface every gateway adapter implements (Section A1, A1.3).
 * The router (Day 7-8) depends only on this interface — it never knows or
 * cares whether it's talking to Razorpay, Stripe, PayU, or UPI.
 */
public interface PaymentGateway {

    /** Must exactly match a gateway_config.gateway_name row (Day 2, V11 seed data). */
    String getGatewayName();

    GatewayResult authorize(AuthorizationRequest request);

    GatewayResult capture(CaptureRequest request);

    GatewayResult voidAuthorization(VoidRequest request);

    GatewayResult refund(RefundRequest request);
}