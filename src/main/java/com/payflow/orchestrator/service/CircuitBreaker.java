package com.payflow.orchestrator.service;

import com.payflow.orchestrator.domain.GatewayConfig;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-gateway, per-payment-method circuit breaker.
 * Thresholds/timeouts come from GatewayConfig,
 * so they're changeable without redeployment, as requires.
 *
 * State is in-memory only (ConcurrentHashMap) — a restart resets every
 * circuit to CLOSED. Persisting circuit state across restarts would need
 * gateway_health_metrics writes (see ADR-004).
 */
@Component
public class CircuitBreaker {

    private final Clock clock;
    private final Map<String, GatewayCircuitState> states = new ConcurrentHashMap<>();

    public CircuitBreaker(Clock clock) {
        this.clock = clock;
    }

    /**
     * Whether a request should be allowed through right now. For HALF_OPEN,
     * this call itself CONSUMES one of the allowed test slots — call it
     * once per actual attempt, not speculatively.
     */
    public synchronized boolean allowRequest(String gateway, String paymentMethod, GatewayConfig config) {
        GatewayCircuitState s = stateFor(gateway, paymentMethod);
        maybeExpireOpenTimeout(s, config);

        return switch (s.state) {
            case CLOSED -> true;
            case OPEN -> false;
            case HALF_OPEN -> {
                if (s.halfOpenCallsIssued < config.getCircuitBreakerHalfOpenMaxCalls()) {
                    s.halfOpenCallsIssued++;
                    yield true;
                }
                yield false;
            }
        };
    }

    public synchronized void recordSuccess(String gateway, String paymentMethod) {
        GatewayCircuitState s = stateFor(gateway, paymentMethod);
        s.consecutiveFailures = 0;
        if (s.state == CircuitState.HALF_OPEN) {
            s.state = CircuitState.CLOSED;
            s.halfOpenCallsIssued = 0;
            s.openedAt = null;
        }
    }

    public synchronized void recordFailure(String gateway, String paymentMethod, GatewayConfig config) {
        GatewayCircuitState s = stateFor(gateway, paymentMethod);

        if (s.state == CircuitState.HALF_OPEN) {
            // The single test request failed — reopen AND reset the timeout window.
            open(s);
            return;
        }

        s.consecutiveFailures++;
        if (s.state == CircuitState.CLOSED && s.consecutiveFailures >= config.getCircuitBreakerFailureThreshold()) {
            open(s);
        }
    }

    public synchronized CircuitState getState(String gateway, String paymentMethod) {
        GatewayCircuitState s = stateFor(gateway, paymentMethod);
        maybeExpireOpenTimeout(s, null); // config only needed if we have a config to check against; see below
        return s.state;
    }

    /** Overload used when a GatewayConfig is available — needed for the actual timeout duration. */
    public synchronized CircuitState getState(String gateway, String paymentMethod, GatewayConfig config) {
        GatewayCircuitState s = stateFor(gateway, paymentMethod);
        maybeExpireOpenTimeout(s, config);
        return s.state;
    }

    private void maybeExpireOpenTimeout(GatewayCircuitState s, GatewayConfig config) {
        if (s.state != CircuitState.OPEN || s.openedAt == null || config == null) {
            return;
        }
        long elapsedSeconds = Duration.between(s.openedAt, Instant.now(clock)).getSeconds();
        if (elapsedSeconds >= config.getCircuitBreakerTimeoutSeconds()) {
            s.state = CircuitState.HALF_OPEN;
            s.halfOpenCallsIssued = 0;
        }
    }

    private void open(GatewayCircuitState s) {
        s.state = CircuitState.OPEN;
        s.openedAt = Instant.now(clock);
        s.halfOpenCallsIssued = 0;
    }

    private GatewayCircuitState stateFor(String gateway, String paymentMethod) {
        return states.computeIfAbsent(gateway + ":" + paymentMethod, k -> new GatewayCircuitState());
    }

    private static class GatewayCircuitState {
        CircuitState state = CircuitState.CLOSED;
        int consecutiveFailures = 0;
        Instant openedAt;
        int halfOpenCallsIssued = 0;
    }
}