package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.ProcessedWebhookEvent;
import com.payflow.orchestrator.repository.ProcessedWebhookEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated helper bean, whose SOLE job is the insert attempt, in its own
 * REQUIRES_NEW transaction. Deliberately a SEPARATE bean from
 * WebhookDedupService (not just a second method on the same class):
 * Spring's @Transactional relies on AOP proxying, and calling
 * this.insert(...) from within another method of the SAME class bypasses
 * the proxy entirely — REQUIRES_NEW would be silently ignored. Splitting
 * into two beans forces a genuine proxy hop, so the isolation actually
 * takes effect. See docs/adr/006 addendum for the full failure mode this
 * fixes (UnexpectedRollbackException).
 */
@Component
public class ProcessedWebhookEventInserter {

    private final ProcessedWebhookEventRepository repository;

    public ProcessedWebhookEventInserter(ProcessedWebhookEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(String gateway, String eventId, String eventType, String payloadHash) {
        repository.saveAndFlush(ProcessedWebhookEvent.record(gateway, eventId, eventType, payloadHash, null));
    }
}