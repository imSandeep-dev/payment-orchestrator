package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}