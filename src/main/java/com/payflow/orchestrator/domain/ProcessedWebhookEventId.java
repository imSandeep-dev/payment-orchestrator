package com.payflow.orchestrator.domain;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class ProcessedWebhookEventId implements Serializable {

    private String gateway;
    private String eventId;

    public ProcessedWebhookEventId(String gateway, String eventId) {
        this.gateway = gateway;
        this.eventId = eventId;
    }
}