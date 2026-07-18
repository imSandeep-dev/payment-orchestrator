package com.payflow.orchestrator.service;

import com.payflow.orchestrator.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


@Component
public class IdempotencyKeyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupJob.class);

    private final IdempotencyKeyRepository repository;

    public IdempotencyKeyCleanupJob(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedRate = 3_600_000) // hourly
    @Transactional
    public void cleanupExpiredKeys() {
        int deleted = repository.deleteAllExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("Idempotency key cleanup: removed {} expired entries", deleted);
        }
    }
}