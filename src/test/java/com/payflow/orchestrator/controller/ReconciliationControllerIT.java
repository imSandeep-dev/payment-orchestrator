package com.payflow.orchestrator.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReconciliationControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private final RestTemplate restTemplate = new RestTemplate();

    @LocalServerPort
    private int port;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<Void> authRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "dev-api-key-change-me");
        return new HttpEntity<>(headers);
    }

    @Test
    void triggerReturnsARunId() {
        ResponseEntity<ReconciliationController.TriggerResponse> response = restTemplate.exchange(
                url("/api/v1/reconciliation/trigger"), HttpMethod.POST, authRequest(),
                ReconciliationController.TriggerResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().runId()).isNotNull();
    }

    @Test
    void reportForFreshRunWithNoDiscrepanciesIsEmpty() {
        ResponseEntity<ReconciliationController.TriggerResponse> triggerResponse = restTemplate.exchange(
                url("/api/v1/reconciliation/trigger"), HttpMethod.POST, authRequest(),
                ReconciliationController.TriggerResponse.class);
        assertThat(triggerResponse.getBody()).isNotNull();
        var runId = triggerResponse.getBody().runId();

        ResponseEntity<ReconciliationController.ReconciliationEntryResponse[]> reportResponse = restTemplate.exchange(
                url("/api/v1/reconciliation/reports/" + runId), HttpMethod.GET, authRequest(),
                ReconciliationController.ReconciliationEntryResponse[].class);

        assertThat(reportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reportResponse.getBody()).isNotNull();
        assertThat(List.of(reportResponse.getBody())).isEmpty();
    }
}