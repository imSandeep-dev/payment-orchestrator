package com.payflow.orchestrator.repository;

import com.payflow.orchestrator.domain.ProcessedWebhookEvent;
import com.payflow.orchestrator.domain.ProcessedWebhookEventId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, ProcessedWebhookEventId> {
}