package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.TransactionStateLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionStateLogRepository extends JpaRepository<TransactionStateLog, UUID> {

    // Backs API endpoint #8 (Section A7.1): GET /api/v1/payments/{id}/timeline
    List<TransactionStateLog> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}