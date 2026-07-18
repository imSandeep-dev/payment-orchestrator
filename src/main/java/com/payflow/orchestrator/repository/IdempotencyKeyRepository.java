package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.IdempotencyKeyEntry;
import com.payflow.orchestrator.domain.IdempotencyKeyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntry, IdempotencyKeyId> {

    @Modifying
    @Query("DELETE FROM IdempotencyKeyEntry e WHERE e.expiresAt < :cutoff")
    int deleteAllExpiredBefore(Instant cutoff);

}