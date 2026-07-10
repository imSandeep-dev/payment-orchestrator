package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.ProcessedWebhookEventId;
import com.payflow.orchestrator.repository.ProcessedWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Atomic check-and-insert. The actual insert attempt lives
 * in ProcessedWebhookEventInserter (a separate bean) so its REQUIRES_NEW
 * transaction is genuinely self-contained — this method itself is
 * deliberately NOT @Transactional, so catching the duplicate-key exception
 * here never has a surrounding transaction of its own that could be left
 * marked roll back-only. See docs/adr/006 addendum.
 */
@Service
public class WebhookDedupService {

    private final ProcessedWebhookEventInserter inserter;
    private final ProcessedWebhookEventRepository repository;

    public WebhookDedupService(ProcessedWebhookEventInserter inserter, ProcessedWebhookEventRepository repository) {
        this.inserter = inserter;
        this.repository = repository;
    }

    public boolean tryMarkProcessed(String gateway, String eventId, String eventType, String payloadHash) {
        try {
            inserter.insert(gateway, eventId, eventType, payloadHash);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false; // (gateway, event_id) already existed -> genuine duplicate
        }
    }

    @Transactional
    public void linkToTransaction(String gateway, String eventId, UUID transactionId) {
        repository.findById(new ProcessedWebhookEventId(gateway, eventId))
                .ifPresent(entry -> {
                    entry.linkToTransaction(transactionId);
                    repository.save(entry);
                });
    }
}