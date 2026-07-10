package com.payflow.orchestrator.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class WebhookDedupServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private WebhookDedupService dedupService;

    @Test
    void firstDeliveryOfAnEventIsMarkedProcessed() {
        boolean isNew = dedupService.tryMarkProcessed(
                "razorpay", "evt_" + UUID.randomUUID(), "payment.captured", "hash1");
        assertThat(isNew).isTrue();
    }

    @Test
    void duplicateDeliveryOfSameEventIsRejected_perFS02() {
        String eventId = "evt_" + UUID.randomUUID();
        dedupService.tryMarkProcessed("razorpay", eventId, "payment.captured", "hash1");

        assertThat(dedupService.tryMarkProcessed("razorpay", eventId, "payment.captured", "hash1")).isFalse();
        assertThat(dedupService.tryMarkProcessed("razorpay", eventId, "payment.captured", "hash1")).isFalse();
    }

    @Test
    void sameEventIdFromDifferentGatewaysAreIndependent_perSectionA54() {
        String sharedEventId = "evt_shared_123";

        assertThat(dedupService.tryMarkProcessed("razorpay", sharedEventId, "payment.captured", "hash1")).isTrue();
        assertThat(dedupService.tryMarkProcessed("stripe", sharedEventId, "payment_intent.succeeded", "hash2")).isTrue();
    }

    @Test
    void linkToTransactionDoesNotResetDedupState() {
        String eventId = "evt_" + UUID.randomUUID();
        dedupService.tryMarkProcessed("razorpay", eventId, "payment.captured", "hash1");

        dedupService.linkToTransaction("razorpay", eventId, UUID.randomUUID());

        assertThat(dedupService.tryMarkProcessed("razorpay", eventId, "payment.captured", "hash1")).isFalse();
    }
}