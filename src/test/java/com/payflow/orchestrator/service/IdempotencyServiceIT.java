package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.IdempotencyKeyEntry;
import com.payflow.orchestrator.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @SpringBootTest, NOT @DataJpaTest: the concurrency test needs each thread
 * to get its own genuine transaction/connection, which @DataJpaTest's
 * single-test-transaction wrapping would defeat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class IdempotencyServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Test
    void firstRequestForANewKeyProceeds() {
        IdempotencyOutcome outcome = idempotencyService.checkAndLock(
                UUID.randomUUID(), UUID.randomUUID().toString(), "hash-1");

        assertThat(outcome.status()).isEqualTo(IdempotencyOutcome.Status.PROCEED);
    }

    @Test
    void duplicateRequestWhileProcessingReturnsConflict_perFS03() {
        UUID merchantId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        idempotencyKeyRepository.saveAndFlush(
                IdempotencyKeyEntry.startProcessing(merchantId, key, "hash-1"));

        IdempotencyOutcome outcome = idempotencyService.checkAndLock(merchantId, key, "hash-1");

        assertThat(outcome.status()).isEqualTo(IdempotencyOutcome.Status.CONFLICT_IN_FLIGHT);
    }

    @Test
    void completedRequestReturnsCachedResponse() {
        UUID merchantId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        IdempotencyKeyEntry entry = IdempotencyKeyEntry.startProcessing(merchantId, key, "hash-1");
        entry.markCompleted(200, "{\"status\":\"ok\"}", UUID.randomUUID());
        idempotencyKeyRepository.saveAndFlush(entry);

        IdempotencyOutcome outcome = idempotencyService.checkAndLock(merchantId, key, "hash-1");

        assertThat(outcome.status()).isEqualTo(IdempotencyOutcome.Status.CACHED_RESPONSE);
        assertThat(outcome.cachedResponseCode()).isEqualTo(200);
        assertThat(outcome.cachedResponseBody()).contains("ok");
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        UUID merchantId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        IdempotencyKeyEntry entry = IdempotencyKeyEntry.startProcessing(merchantId, key, "hash-original");
        entry.markCompleted(200, "{}", UUID.randomUUID());
        idempotencyKeyRepository.saveAndFlush(entry);

        IdempotencyOutcome outcome = idempotencyService.checkAndLock(merchantId, key, "hash-DIFFERENT");

        assertThat(outcome.status()).isEqualTo(IdempotencyOutcome.Status.KEY_REUSED_DIFFERENT_PAYLOAD);
    }

    @Test
    void expiredKeyAllowsFreshAttempt_perSectionA42() {
        UUID merchantId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        IdempotencyKeyEntry entry = IdempotencyKeyEntry.startProcessing(merchantId, key, "hash-1");
        entry.markCompleted(200, "{}", UUID.randomUUID());
        // No public setter for expiresAt (deliberate — see Day 4's no-setter
        // discipline). ReflectionTestUtils lets the TEST simulate "24 hours
        // have passed" without weakening the entity's real API.
        ReflectionTestUtils.setField(entry, "expiresAt", Instant.now().minus(1, ChronoUnit.HOURS));
        idempotencyKeyRepository.saveAndFlush(entry);

        IdempotencyOutcome outcome = idempotencyService.checkAndLock(merchantId, key, "hash-NEW");

        assertThat(outcome.status()).isEqualTo(IdempotencyOutcome.Status.PROCEED);
    }

    @Test
    void failedKeyAllowsRetry_perSectionA41() {
        UUID merchantId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        IdempotencyKeyEntry entry = IdempotencyKeyEntry.startProcessing(merchantId, key, "hash-1");
        entry.markFailed();
        idempotencyKeyRepository.saveAndFlush(entry);

        IdempotencyOutcome outcome = idempotencyService.checkAndLock(merchantId, key, "hash-1");

        assertThat(outcome.status()).isEqualTo(IdempotencyOutcome.Status.PROCEED);
    }

    @Test
    void sameKeyDifferentMerchants_treatedAsIndependentRequests_perFS13() {
        String sharedKey = UUID.randomUUID().toString();

        IdempotencyOutcome resultA = idempotencyService.checkAndLock(UUID.randomUUID(), sharedKey, "hash-a");
        IdempotencyOutcome resultB = idempotencyService.checkAndLock(UUID.randomUUID(), sharedKey, "hash-b");

        assertThat(resultA.status()).isEqualTo(IdempotencyOutcome.Status.PROCEED);
        assertThat(resultB.status()).isEqualTo(IdempotencyOutcome.Status.PROCEED);
    }

    @Test
    void concurrentRequestsWithSameKey_onlyOneProceeds_perFS09() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        List<Future<IdempotencyOutcome>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return idempotencyService.checkAndLock(merchantId, key, "same-hash");
            }));
        }

        readyLatch.await();     // both threads ready at the starting line
        startLatch.countDown(); // release both simultaneously

        List<IdempotencyOutcome> results = new ArrayList<>();
        for (Future<IdempotencyOutcome> f : futures) {
            results.add(f.get(5, TimeUnit.SECONDS));
        }
        executor.shutdown();

        long proceedCount = results.stream().filter(r -> r.status() == IdempotencyOutcome.Status.PROCEED).count();
        long conflictCount = results.stream().filter(r -> r.status() == IdempotencyOutcome.Status.CONFLICT_IN_FLIGHT).count();

        // The advisory lock must serialize these — under NO timing scenario
        // should both succeed. This is the test FS-09 explicitly requires.
        assertThat(proceedCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);
    }
}