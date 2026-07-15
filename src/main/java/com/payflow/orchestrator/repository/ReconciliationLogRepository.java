package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.ReconciliationLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationLogRepository extends JpaRepository<ReconciliationLogEntry, UUID> {
    List<ReconciliationLogEntry> findByRunIdOrderByCreatedAtAsc(UUID runId);
}