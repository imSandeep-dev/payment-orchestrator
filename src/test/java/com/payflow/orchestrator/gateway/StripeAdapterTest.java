package com.payflow.orchestrator.gateway;

class StripeAdapterTest extends PaymentGatewayContractTest {
    @Override
    protected PaymentGateway gateway() {
        return new StripeAdapter();
    }
}