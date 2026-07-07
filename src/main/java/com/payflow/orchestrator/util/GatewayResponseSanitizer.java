package com.payflow.orchestrator.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Redacts known PII field VALUES (keeping the keys, so the payload shape is
 * still debuggable) from a gateway response before it is written to
 * transaction_state_log.gateway_response (Section A2.3).
 * JACKSON 3 NOTE: Spring Boot 4 defaults to Jackson 3, whose package root
 * moved from com.fasterxml.jackson to tools.jackson (jackson-annotations is
 * the one exception, staying under com.fasterxml.jackson.annotation — not
 * used in this class). If these imports fail to resolve against your exact
 * dependency versions, tell me the compiler error, and we'll adjust — the
 * fallback is adding the spring-boot-jackson2 bridge module and switching
 * back to com.fasterxml.jackson.databind.ObjectMapper.
 */
@Component
public class GatewayResponseSanitizer {

    private static final Set<String> PII_FIELD_NAMES = Set.of(
            "card_number", "cvv", "cvv2", "card_holder_name",
            "email", "customer_email", "phone", "customer_phone",
            "customer_name", "bank_account_number", "account_number",
            "vpa", "upi_id", "ifsc"
    );

    private static final String REDACTED_PLACEHOLDER = "***REDACTED***";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public String sanitize(String rawJson) throws JsonProcessingException {
        if (rawJson == null || rawJson.isBlank()) {
            return rawJson;
        }
        JsonNode root = jsonMapper.readTree(rawJson);
        JsonNode redacted = redactRecursively(root);
        return jsonMapper.writeValueAsString(redacted);
    }

    private JsonNode redactRecursively(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            // Collect field names first — mutating an ObjectNode while
            // iterating its own field-name iterator throws
            // ConcurrentModificationException.
            List<String> fieldNames = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldNames::add);

            for (String fieldName : fieldNames) {
                if (isPiiField(fieldName)) {
                    objectNode.put(fieldName, REDACTED_PLACEHOLDER);
                } else {
                    JsonNode value = objectNode.get(fieldName);
                    if (value.isObject() || value.isArray()) {
                        objectNode.replace(fieldName, redactRecursively(value));
                    }
                }
            }
            return objectNode;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode element = arrayNode.get(i);
                if (element.isObject() || element.isArray()) {
                    arrayNode.set(i, redactRecursively(element));
                }
            }
            return arrayNode;
        }
        return node;
    }

    private boolean isPiiField(String fieldName) {
        return PII_FIELD_NAMES.contains(fieldName.toLowerCase());
    }
}