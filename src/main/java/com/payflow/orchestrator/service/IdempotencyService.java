package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.IdempotencyKeyEntry;
import com.payflow.orchestrator.domain.IdempotencyKeyId;
import com.payflow.orchestrator.repository.IdempotencyKeyRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements idempotency algorithm
 * advisory-lock pattern. Every public method here is @Transactional — the
 * advisory lock is transaction-scoped (auto-released on commit/rollback),
 * so the lock's lifetime is exactly the method call's lifetime.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public IdempotencyService(IdempotencyKeyRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public IdempotencyOutcome checkAndLock(UUID merchantId, String idempotencyKey, String requestHash) {
        acquireAdvisoryLock(merchantId, idempotencyKey);

        IdempotencyKeyId id = new IdempotencyKeyId(merchantId, idempotencyKey);
        Optional<IdempotencyKeyEntry> existing = repository.findById(id);

        if (existing.isPresent() && shouldDiscardAndRetry(existing.get())) {
            repository.delete(existing.get());
            repository.flush();
            existing = Optional.empty();
        }

        if (existing.isPresent()) {
            IdempotencyKeyEntry entry = existing.get();
            if (entry.isProcessing()) {
                return IdempotencyOutcome.conflict();
            }
            // Only remaining possibility: COMPLETED (FAILED/expired were already discarded above).
            if (!entry.getRequestHash().equals(requestHash)) {
                return IdempotencyOutcome.keyReusedWithDifferentPayload();
            }
            return IdempotencyOutcome.cached(entry.getResponseCode(), entry.getResponseBody());
        }

        IdempotencyKeyEntry fresh = IdempotencyKeyEntry.startProcessing(merchantId, idempotencyKey, requestHash);
        repository.saveAndFlush(fresh);
        return IdempotencyOutcome.proceed();
    }

    @Transactional
    public void markCompleted(UUID merchantId, String idempotencyKey, int responseCode,
                              String responseBodyJson, UUID transactionId) {
        IdempotencyKeyEntry entry = repository.findById(new IdempotencyKeyId(merchantId, idempotencyKey))
                .orElseThrow(() -> new IllegalStateException("No idempotency entry to complete: " + idempotencyKey));
        entry.markCompleted(responseCode, responseBodyJson, transactionId);
    }

    @Transactional
    public void markFailed(UUID merchantId, String idempotencyKey) {
        IdempotencyKeyEntry entry = repository.findById(new IdempotencyKeyId(merchantId, idempotencyKey))
                .orElseThrow(() -> new IllegalStateException("No idempotency entry to fail: " + idempotencyKey));
        entry.markFailed();
    }

    /**
     * A key entry is discarded (letting the caller start fresh) if it's past
     * its 24h TTL or the prior attempt FAILED ("on failure,
     * mark key as failed (allow retry)"). PROCESSING and COMPLETED entries
     * are never discarded here.
     *
     * NOTE: this happens at READ time, not via a background job — the
     * scheduled cleanup mentioned in SQL comment is a later concern, and reads must not depend on it having already run.
     */
    private boolean shouldDiscardAndRetry(IdempotencyKeyEntry entry) {
        return entry.isFailed() || entry.getExpiresAt().isBefore(Instant.now());
    }

    /**
     * Pg_advisory_xact_lock(hashtext(?)) — exact pattern.
     * Parameterized (PreparedStatement), never string-concatenated: the
     * lock key material includes a client-supplied idempotency key, so
     * building raw SQL text from it would be a genuine SQL injection risk.
     */
    private void acquireAdvisoryLock(UUID merchantId, String idempotencyKey) {
        String lockKeySource = merchantId + ":" + idempotencyKey;
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                ps.setString(1, lockKeySource);
                ps.execute();
            }
            return null;
        });
    }
}