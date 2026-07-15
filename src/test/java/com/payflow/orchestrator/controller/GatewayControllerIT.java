package com.payflow.orchestrator.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GatewayControllerIT {

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
    void listReturnsAllFourSeededGateways() {
        ResponseEntity<GatewayController.GatewaySummary[]> response = restTemplate.exchange(
                url("/api/v1/gateways"), HttpMethod.GET, authRequest(), GatewayController.GatewaySummary[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        List<GatewayController.GatewaySummary> gateways = List.of(response.getBody());
        assertThat(gateways).hasSize(4);
        assertThat(gateways).extracting(GatewayController.GatewaySummary::gatewayName)
                .containsExactlyInAnyOrder("razorpay", "stripe", "payu", "upi");
    }

    @Test
    void healthForKnownGatewayReturnsClosedCircuit() {
        ResponseEntity<GatewayController.GatewayHealth> response = restTemplate.exchange(
                url("/api/v1/gateways/razorpay/health"), HttpMethod.GET, authRequest(), GatewayController.GatewayHealth.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().circuitState()).isEqualTo("CLOSED");
    }

    @Test
    void healthForUnknownGatewayReturns404() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/api/v1/gateways/nonexistent/health"), HttpMethod.GET, authRequest(), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        } catch (HttpStatusCodeException ex) {
            // Some RestTemplate/error-handler configurations throw on 4xx/5xx
            // instead of returning a ResponseEntity — handle both paths so this
            // test isn't coupled to that implementation detail.
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void updateConfigPersistsChanges() {
        GatewayController.UpdateGatewayConfigRequest request = new GatewayController.UpdateGatewayConfigRequest(
                true, new BigDecimal("0.03"), 250, 180, 4, 25, 2);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "dev-api-key-change-me");

        ResponseEntity<GatewayController.GatewaySummary> response = restTemplate.exchange(
                url("/api/v1/gateways/razorpay/config"), HttpMethod.PUT,
                new HttpEntity<>(request, headers), GatewayController.GatewaySummary.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isTrue();
    }
}