package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.IdempotencyKeyEntry;
import com.payflow.orchestrator.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Validates Section B3's "Idempotency Check < 10ms" target directly. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class IdempotencyPerformanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private IdempotencyService idempotencyService;
    @Autowired private IdempotencyKeyRepository repository;

    @Test
    void cachedResponseLookupCompletesUnderTenMilliseconds() {
        UUID merchantId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        IdempotencyKeyEntry entry = IdempotencyKeyEntry.startProcessing(merchantId, key, "hash-1");
        entry.markCompleted(200, "{\"status\":\"ok\"}", UUID.randomUUID());
        repository.saveAndFlush(entry);

        // Warm up (JIT, connection pool) before measuring — a cold first
        // call isn't representative of steady-state production latency.
        idempotencyService.checkAndLock(merchantId, key, "hash-1");

        long start = System.nanoTime();
        idempotencyService.checkAndLock(merchantId, key, "hash-1");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(10);
    }
}