package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.GatewayHealthMetricsSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/** Confirms the historical seed data loaded correctly. */
@DataJpaTest
@Testcontainers
class GatewayHealthMetricsSeedDataIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private GatewayHealthMetricsSnapshotRepository repository;

    @Test
    void allSixteenHistoricalWindowsAreSeeded() {
        List<GatewayHealthMetricsSnapshot> all = repository.findAll();
        assertThat(all).hasSize(16); // 4 gateways x 4 six-hour windows
    }

    @Test
    void razorpayMostRecentWindowMatchesA34Dataset() {
        GatewayHealthMetricsSnapshot latest = repository
                .findTopByGatewayAndPaymentMethodOrderByWindowStartDesc("razorpay", "ALL")
                .orElseThrow();

        assertThat(latest.getTotalCount()).isEqualTo(25300);
        assertThat(latest.getSuccessCount() + latest.getFailureCount()).isEqualTo(latest.getTotalCount());
        assertThat(latest.successRate()).isCloseTo(0.968, offset(0.001));
    }
}