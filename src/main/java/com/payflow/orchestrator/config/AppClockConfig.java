package com.payflow.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides a single injectable Clock bean. Time-dependent services (like
 * CircuitBreaker's 30-second OPEN timeout) can then be unit-tested with a
 * fast-forwardable test clock instead of real Thread.sleep() calls.
 */
@Configuration
public class AppClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}