package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.IdempotencyKeyEntry;
import com.payflow.orchestrator.domain.IdempotencyKeyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntry, IdempotencyKeyId> {
}