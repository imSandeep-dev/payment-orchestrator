package com.payflow.orchestrator.gateway;

class RazorpayAdapterTest extends PaymentGatewayContractTest {
    @Override
    protected PaymentGateway gateway() {
        return new RazorpayAdapter();
    }
}