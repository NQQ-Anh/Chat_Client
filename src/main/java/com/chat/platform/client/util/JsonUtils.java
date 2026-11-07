package com.chat.platform.client.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public final class JsonUtils {
    // Bỏ qua field lạ để client không hỏng khi server thêm thuộc tính mới.
    public static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtils() {
    }

    public static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode request JSON.", e);
        }
    }

    public static String errorText(String body) {
        try {
            JsonNode json = JSON.readTree(body);
            return json.path("message").asText(json.path("error").asText("Request failed."));
        } catch (IOException e) {
            return body == null || body.isBlank() ? "Request failed." : body;
        }
    }
}
