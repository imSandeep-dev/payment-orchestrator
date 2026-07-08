package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.GatewayConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GatewayConfigRepository extends JpaRepository<GatewayConfig, String> {
    List<GatewayConfig> findByEnabledTrue();
}