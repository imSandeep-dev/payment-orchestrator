package com.payflow.orchestrator.webhook;

import com.payflow.orchestrator.util.HmacUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private WebhookSecretsProperties secretsFor(String gateway, String secret) {
        WebhookSecretsProperties props = new WebhookSecretsProperties();
        props.getGateways().put(gateway, secret);
        return props;
    }

    @Test
    void razorpayVerifierAcceptsCorrectSignature() {
        var verifier = new RazorpayWebhookSignatureVerifier(secretsFor("razorpay", "test-secret"));
        String body = "{\"event\":\"payment.captured\"}";
        String valid = HmacUtil.hmacHex("HmacSHA256", "test-secret", body);

        assertThat(verifier.verify(body, valid)).isTrue();
    }

    @Test
    void razorpayVerifierRejectsIncorrectSignature() {
        var verifier = new RazorpayWebhookSignatureVerifier(secretsFor("razorpay", "test-secret"));

        assertThat(verifier.verify("{\"event\":\"payment.captured\"}", "0000invalidsignature")).isFalse();
    }

    @Test
    void razorpayVerifierRejectsTamperedPayload_perFS10() {
        var verifier = new RazorpayWebhookSignatureVerifier(secretsFor("razorpay", "test-secret"));
        String originalBody = "{\"amount\":10000}";
        String validForOriginal = HmacUtil.hmacHex("HmacSHA256", "test-secret", originalBody);
        String tamperedBody = "{\"amount\":1000000}";

        assertThat(verifier.verify(tamperedBody, validForOriginal)).isFalse();
    }

    @Test
    void payuVerifierUsesHmacSha512_perSectionA13() {
        var verifier = new PayUWebhookSignatureVerifier(secretsFor("payu", "payu-secret"));
        String body = "{\"txnid\":\"abc123\"}";
        String valid = HmacUtil.hmacHex("HmacSHA512", "payu-secret", body);

        assertThat(verifier.verify(body, valid)).isTrue();
    }

    @Test
    void upiVerifierAcceptsCorrectSignature() {
        var verifier = new UpiWebhookSignatureVerifier(secretsFor("upi", "upi-secret"));
        String body = "{\"txnRef\":\"UPI123\"}";
        String valid = HmacUtil.hmacHex("HmacSHA256", "upi-secret", body);

        assertThat(verifier.verify(body, valid)).isTrue();
    }

    @Test
    void stripeVerifierAcceptsCorrectSignatureWithTimestampFormat() {
        var verifier = new StripeWebhookSignatureVerifier(secretsFor("stripe", "stripe-secret"));
        String body = "{\"type\":\"payment_intent.succeeded\"}";
        String timestamp = "1751900000";
        String signature = HmacUtil.hmacHex("HmacSHA256", "stripe-secret", timestamp + "." + body);
        String header = "t=" + timestamp + ",v1=" + signature;

        assertThat(verifier.verify(body, header)).isTrue();
    }

    @Test
    void stripeVerifierRejectsMalformedHeader() {
        var verifier = new StripeWebhookSignatureVerifier(secretsFor("stripe", "stripe-secret"));

        assertThat(verifier.verify("{}", "not-a-valid-stripe-header")).isFalse();
    }

    @Test
    void allVerifiersRejectNullOrBlankSignatureHeader() {
        var verifier = new RazorpayWebhookSignatureVerifier(secretsFor("razorpay", "test-secret"));

        assertThat(verifier.verify("{}", null)).isFalse();
        assertThat(verifier.verify("{}", "")).isFalse();
    }
}