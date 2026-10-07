package com.xq.exerciseservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;

class OpenApiContractTests {
    @Test
    void exerciseContractParsesWithoutErrors() {
        try (InputStream contract = getClass().getResourceAsStream("/static/openapi/exercise-service.yaml")) {
            var result = new OpenAPIV3Parser().readContents(
                    new String(contract.readAllBytes(), StandardCharsets.UTF_8), null, null);
            assertFalse(result.getMessages().stream().anyMatch(message -> message.startsWith("attribute paths")));
            assertNotNull(result.getOpenAPI());
            assertFalse(result.getOpenAPI().getPaths().isEmpty());
        } catch (Exception exception) {
            throw new AssertionError("OpenAPI contract could not be read", exception);
        }
    }
}
