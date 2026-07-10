package com.payflow.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 *
 * Implements Persistable<ProcessedWebhookEventId> to fix a real bug found
 * with a manually assigned (non-@GeneratedValue) composite ID,
 * Spring Data JPA's default isNew() check ("is the ID null?") is always
 * false here, since record() always assigns an ID before save(). That made
 * repository.save() silently call entityManager.merge() (an upsert) for
 * EVERY call, including the "duplicate" case WebhookDedupService relies on
 * failing with a genuine unique-constraint violation. isNewFlag, set true
 * only inside record() and left at its false default whenever Hibernate
 * reconstitutes this entity from the database, restores the correct
 * insert-vs.-update distinction.
 */
@Entity
@Table(name = "processed_webhook_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedWebhookEvent implements Persistable<ProcessedWebhookEventId> {

    @EmbeddedId
    private ProcessedWebhookEventId id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /**
     * Deliberately NOT persisted. Defaults false — which is exactly what
     * every entity Hibernate loads FROM the database gets, since hydration
     * never touches this transient field. Only record() (below) flips it
     * true, for the one save() call that must be a genuine INSERT.
     */
    @Transient
    private boolean isNewFlag = false;

    public static ProcessedWebhookEvent record(String gateway, String eventId, String eventType,
                                               String payloadHash, UUID transactionId) {
        ProcessedWebhookEvent e = new ProcessedWebhookEvent();
        e.id = new ProcessedWebhookEventId(gateway, eventId);
        e.eventType = eventType;
        e.payloadHash = payloadHash;
        e.transactionId = transactionId;
        e.processedAt = Instant.now();
        e.isNewFlag = true;
        return e;
    }

    public void linkToTransaction(UUID transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public boolean isNew() {
        return isNewFlag;
    }
}