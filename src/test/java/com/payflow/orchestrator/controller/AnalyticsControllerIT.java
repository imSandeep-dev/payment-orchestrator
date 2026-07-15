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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AnalyticsControllerIT {

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
    void volumeWithNoFilterReturnsCountAcrossAllTransactions() {
        ResponseEntity<AnalyticsController.VolumeResponse> response = restTemplate.exchange(
                url("/api/v1/analytics/volume"), HttpMethod.GET, authRequest(), AnalyticsController.VolumeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().transactionCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void successRateForGatewayWithNoTransactionsReturnsZeroNotError() {
        ResponseEntity<AnalyticsController.SuccessRateResponse> response = restTemplate.exchange(
                url("/api/v1/analytics/success-rate?gateway=payu"), HttpMethod.GET, authRequest(),
                AnalyticsController.SuccessRateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().successRate()).isEqualTo(0.0);
    }
}