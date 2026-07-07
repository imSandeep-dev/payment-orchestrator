package com.payflow.orchestrator.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayResponseSanitizerTest {

    private final GatewayResponseSanitizer sanitizer = new GatewayResponseSanitizer();

    @Test
    void redactsKnownPiiFieldsButPreservesStructure() throws JsonProcessingException {
        String raw = """
                {
                  "status": "captured",
                  "card_number": "4111111111111111",
                  "customer_email": "test@example.com",
                  "amount": 120050,
                  "metadata": { "phone": "9876543210", "order_id": "ORD-123" }
                }
                """;

        String sanitized = sanitizer.sanitize(raw);

        assertThat(sanitized)
                .contains("\"status\":\"captured\"")
                .contains("\"card_number\":\"***REDACTED***\"")
                .contains("\"customer_email\":\"***REDACTED***\"")
                .contains("\"phone\":\"***REDACTED***\"")
                .contains("\"order_id\":\"ORD-123\"")
                .contains("120050")
                .doesNotContain("4111111111111111")
                .doesNotContain("test@example.com")
                .doesNotContain("9876543210");
    }

    @Test
    void handlesNullAndBlankInputGracefully() throws JsonProcessingException {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("")).isEmpty();
    }

    @Test
    void redactsPiiInsideArrays() throws JsonProcessingException {
        String raw = """
                { "line_items": [
                    {"card_number": "4111", "amount": 100},
                    {"card_number": "5222", "amount": 200}
                ] }
                """;

        String sanitized = sanitizer.sanitize(raw);

        assertThat(sanitized)
                .doesNotContain("4111")
                .doesNotContain("5222")
                .contains("100")
                .contains("200");
    }
}