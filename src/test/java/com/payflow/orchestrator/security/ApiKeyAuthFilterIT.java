package com.payflow.orchestrator.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ApiKeyAuthFilterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private final RestTemplate restTemplate = new RestTemplate();

    @LocalServerPort
    private int port;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void missingApiKeyReturns401() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/api/v1/routing/config"), HttpMethod.GET, HttpEntity.EMPTY, String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        } catch (HttpStatusCodeException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void wrongApiKeyReturns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "not-a-real-key");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/api/v1/routing/config"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        } catch (HttpStatusCodeException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void correctApiKeyReachesController() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "dev-api-key-change-me");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/routing/config"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void healthEndpointIsExemptFromAuth() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/health"), HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}