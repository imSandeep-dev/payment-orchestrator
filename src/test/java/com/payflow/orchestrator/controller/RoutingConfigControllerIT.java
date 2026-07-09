package com.payflow.orchestrator.controller;

import com.payflow.orchestrator.domain.RoutingConfig;
import com.payflow.orchestrator.repository.RoutingConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses plain RestTemplate rather than Boot's TestRestTemplate — Boot 4
 * relocated TestRestTemplate's auto-configuration into a separate module
 * (spring-boot-resttestclient) with a registration mechanism I couldn't
 * pin down reliably across two attempts. Plain RestTemplate has no
 * Boot-specific auto-configuration to get wrong, at the cost of having to
 * handle 4xx/5xx as thrown exceptions ourselves instead of getting them
 * back as a ResponseEntity.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RoutingConfigControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private RoutingConfigRepository repository;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void getReturnsSeededDefaultConfig() {
        ResponseEntity<RoutingConfigResponse> response =
                restTemplate.getForEntity(url("/api/v1/routing/config"), RoutingConfigResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().configKey()).isEqualTo("default");
        assertThat(response.getBody().weightSuccessRate()).isEqualByComparingTo("0.350");
    }

    @Test
    void putUpdatesWeightsAndPreservesCreatedAt() {
        Instant originalCreatedAt = repository.findById("default").orElseThrow().getCreatedAt();

        UpdateRoutingConfigRequest request = new UpdateRoutingConfigRequest(
                new BigDecimal("0.40"), new BigDecimal("0.20"), new BigDecimal("0.15"),
                new BigDecimal("0.15"), new BigDecimal("0.10"), new BigDecimal("0.25"), 10);

        ResponseEntity<RoutingConfigResponse> response = restTemplate.exchange(
                url("/api/v1/routing/config"), HttpMethod.PUT,
                new HttpEntity<>(request), RoutingConfigResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().weightSuccessRate()).isEqualByComparingTo("0.40");
        assertThat(response.getBody().slidingWindowMinutes()).isEqualTo(10);

        RoutingConfig reloaded = repository.findById("default").orElseThrow();
        assertThat(reloaded.getCreatedAt()).isEqualTo(originalCreatedAt); // NOT overwritten by the update
    }

    @Test
    void putRejectsWeightsThatDoNotSumToOne() {
        UpdateRoutingConfigRequest badRequest = new UpdateRoutingConfigRequest(
                new BigDecimal("0.50"), new BigDecimal("0.50"), new BigDecimal("0.50"),
                new BigDecimal("0.50"), new BigDecimal("0.50"), new BigDecimal("0.20"), 5);

        // Unlike TestRestTemplate, plain RestTemplate throws on 4xx/5xx
        // instead of returning them as a normal ResponseEntity.
        assertThatThrownBy(() -> restTemplate.exchange(
                url("/api/v1/routing/config"), HttpMethod.PUT,
                new HttpEntity<>(badRequest), RoutingConfigResponse.class))
                .isInstanceOf(HttpStatusCodeException.class);
    }

    @BeforeEach
    void resetRoutingConfigToSeededDefaults() {
        RoutingConfig config = repository.findById("default").orElseThrow();
        config.applyWeights(new BigDecimal("0.350"), new BigDecimal("0.200"), new BigDecimal("0.200"),
                new BigDecimal("0.150"), new BigDecimal("0.100"), new BigDecimal("0.200"), 5);
        repository.saveAndFlush(config);
    }
}