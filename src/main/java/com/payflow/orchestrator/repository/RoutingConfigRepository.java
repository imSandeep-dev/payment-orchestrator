package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.RoutingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutingConfigRepository extends JpaRepository<RoutingConfig, String> {
}