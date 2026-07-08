package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.GatewayHealthMetricsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GatewayHealthMetricsSnapshotRepository extends JpaRepository<GatewayHealthMetricsSnapshot, Long> {
    Optional<GatewayHealthMetricsSnapshot> findTopByGatewayAndPaymentMethodOrderByWindowStartDesc(
            String gateway, String paymentMethod);
}