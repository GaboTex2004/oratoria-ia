package com.oratoria.backend.shared.response;

import java.time.Instant;
import java.util.Map;

public record ApiError(Instant timestamp, int status, String error, String message,
                       String path, Map<String, String> fieldErrors) {
    public ApiError(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path, Map.of());
    }
}
