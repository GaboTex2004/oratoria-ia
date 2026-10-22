package com.oratoria.backend.analysis.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class HttpAiSpeechAnalysisClient implements AiSpeechAnalysisClient {
    private static final Logger log = LoggerFactory.getLogger(HttpAiSpeechAnalysisClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String analyzeUrl;
    private final String internalApiKey;
    private final Duration requestTimeout;

    public HttpAiSpeechAnalysisClient(
            ObjectMapper objectMapper,
            @Value("${app.ai-service.url:http://localhost:8000}") String baseUrl,
            @Value("${app.ai-service.internal-api-key:dev-internal-oratoria-key-change-in-prod}") String internalApiKey,
            @Value("${app.ai-service.timeout-seconds:65}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.analyzeUrl = cleanBase + "/api/v1/speech/analyze";
        this.internalApiKey = internalApiKey != null ? internalApiKey.trim() : "";
        this.requestTimeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        // Explicitly use HTTP_1_1 so HttpClient does not send cleartext HTTP/2 upgrade headers
        // (Connection: Upgrade, HTTP2-Settings / Upgrade: h2c), which cause Uvicorn/httptools
        // to log "Unsupported upgrade request", drop the binary body, and return HTTP 400.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public AiSpeechAnalysisPayload analyzeSpeech(
            UUID sessionId,
            byte[] audioBytes,
            String mimeType,
            int durationSeconds,
            String filename) {
        String requestId = UUID.randomUUID().toString();
        String cleanMime = normalizeMimeType(mimeType);
        String safeFilename = sanitizeFilename(filename, "session-" + sessionId + ".webm");
        String boundary = "----OratoriaAISpringBoundary" + UUID.randomUUID().toString().replace("-", "");

        byte[] multipartBody = buildMultipartBody(
                boundary,
                sessionId.toString(),
                durationSeconds,
                safeFilename,
                cleanMime,
                audioBytes != null ? audioBytes : new byte[0]
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(analyzeUrl))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(requestTimeout)
                .header("X-Internal-Api-Key", internalApiKey)
                .header("X-Request-Id", requestId)
                .header("X-Session-Id", sessionId.toString())
                .header("X-Audio-Duration-Seconds", String.valueOf(durationSeconds))
                .header("X-Audio-Filename", safeFilename)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                .build();

        long startNanos = System.nanoTime();
        log.info(
                "Sending speech analysis request [requestId={} sessionId={} audioBytes={} mime={} durationSeconds={}]",
                requestId,
                sessionId,
                audioBytes != null ? audioBytes.length : 0,
                cleanMime,
                durationSeconds
        );

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            int status = response.statusCode();
            String respRequestId = response.headers().firstValue("X-Request-Id").orElse(requestId);

            if (status >= 200 && status < 300) {
                final AiSpeechAnalysisPayload payload;
                try {
                    payload = objectMapper.readValue(response.body(), AiSpeechAnalysisPayload.class);
                } catch (Exception parseEx) {
                    log.warn(
                            "Speech analysis returned invalid JSON [requestId={} sessionId={} status={} elapsedMs={} code=INVALID_PROVIDER_RESPONSE]",
                            respRequestId,
                            sessionId,
                            status,
                            elapsedMs
                    );
                    throw new AiAnalysisClientException(
                            "INVALID_PROVIDER_RESPONSE",
                            "El servicio de análisis de voz devolvió un formato JSON inválido.",
                            true
                    );
                }
                if (payload == null || payload.metrics() == null) {
                    log.warn(
                            "Speech analysis returned incomplete payload [requestId={} sessionId={} status={} elapsedMs={} code=INVALID_PROVIDER_RESPONSE]",
                            respRequestId,
                            sessionId,
                            status,
                            elapsedMs
                    );
                    throw new AiAnalysisClientException(
                            "INVALID_PROVIDER_RESPONSE",
                            "El servicio de análisis de voz devolvió una respuesta incompleta.",
                            true
                    );
                }
                log.info(
                        "Speech analysis completed [requestId={} sessionId={} status={} elapsedMs={} provider={} model={}]",
                        respRequestId,
                        sessionId,
                        status,
                        elapsedMs,
                        payload.provider(),
                        payload.model()
                );
                return payload;
            }
            throw parseErrorResponse(sessionId, respRequestId, status, elapsedMs, response.body());
        } catch (HttpTimeoutException timeoutException) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.warn(
                    "Speech analysis request timed out [requestId={} sessionId={} elapsedMs={} origin=ai-service-http code=AI_SERVICE_TIMEOUT]",
                    requestId,
                    sessionId,
                    elapsedMs
            );
            throw new AiAnalysisClientException(
                    "AI_SERVICE_TIMEOUT",
                    "El servicio de análisis de voz agotó el tiempo de espera.",
                    true
            );
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.warn(
                    "Speech analysis request interrupted [requestId={} sessionId={} origin=ai-service-http code=AI_SERVICE_INTERRUPTED]",
                    requestId,
                    sessionId
            );
            throw new AiAnalysisClientException(
                    "AI_SERVICE_INTERRUPTED",
                    "El análisis de voz fue interrumpido.",
                    true
            );
        } catch (IOException ioException) {
            log.warn(
                    "Speech analysis service unavailable [requestId={} sessionId={} origin=ai-service-http code=AI_SERVICE_UNAVAILABLE reason={}]",
                    requestId,
                    sessionId,
                    ioException.getClass().getSimpleName()
            );
            throw new AiAnalysisClientException(
                    "AI_SERVICE_UNAVAILABLE",
                    "El servicio de IA no está disponible en este momento. Verifica que FastAPI esté en ejecución.",
                    true
            );
        }
    }

    static byte[] buildMultipartBody(
            String boundary,
            String sessionId,
            int durationSeconds,
            String filename,
            String contentType,
            byte[] fileBytes) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(fileBytes.length + 512);
            writeFormField(out, boundary, "session_id", sessionId);
            writeFormField(out, boundary, "duration_seconds", String.valueOf(durationSeconds));
            writeFormField(out, boundary, "filename", filename);

            String filePartHeader = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n";
            out.write(filePartHeader.getBytes(StandardCharsets.UTF_8));
            out.write(fileBytes);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException unexpected) {
            throw new IllegalStateException("No fue posible construir el cuerpo multipart de audio.", unexpected);
        }
    }

    private static void writeFormField(
            ByteArrayOutputStream out,
            String boundary,
            String fieldName,
            String value) throws IOException {
        String part = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n\r\n"
                + value + "\r\n";
        out.write(part.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeMimeType(String rawMime) {
        if (rawMime == null || rawMime.isBlank()) {
            return "audio/webm";
        }
        int semicolon = rawMime.indexOf(';');
        String base = semicolon >= 0 ? rawMime.substring(0, semicolon) : rawMime;
        String trimmed = base.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? "audio/webm" : trimmed;
    }

    private static String sanitizeFilename(String rawFilename, String fallback) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return fallback;
        }
        String sanitized = rawFilename.replace("\"", "").replace("\r", "").replace("\n", "").trim();
        return sanitized.isEmpty() ? fallback : sanitized;
    }

    private AiAnalysisClientException parseErrorResponse(
            UUID sessionId,
            String requestId,
            int statusCode,
            long elapsedMs,
            String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String code = node.hasNonNull("code") ? node.get("code").asText() : "AI_SERVICE_ERROR_" + statusCode;
            String message = node.hasNonNull("message")
                    ? node.get("message").asText()
                    : "Error en el servicio de análisis de voz (HTTP " + statusCode + ").";
            String origin = node.hasNonNull("origin") ? node.get("origin").asText() : "ai-service";
            String upstreamReqId = node.hasNonNull("requestId") ? node.get("requestId").asText() : requestId;
            boolean retryable = node.hasNonNull("retryable")
                    ? node.get("retryable").asBoolean()
                    : (statusCode == 429 || statusCode >= 500);

            log.warn(
                    "Speech analysis failed [requestId={} sessionId={} status={} elapsedMs={} origin={} code={} retryable={}]",
                    upstreamReqId,
                    sessionId,
                    statusCode,
                    elapsedMs,
                    origin,
                    code,
                    retryable
            );
            return new AiAnalysisClientException(code, message, retryable);
        } catch (Exception ignored) {
            boolean retryable = statusCode == 429 || statusCode >= 500;
            String code = statusCode == 401 || statusCode == 403
                    ? "UNAUTHORIZED_INTERNAL_CALL"
                    : "AI_SERVICE_HTTP_" + statusCode;
            log.warn(
                    "Speech analysis failed with non-JSON response [requestId={} sessionId={} status={} elapsedMs={} origin=ai-service-http code={} retryable={}]",
                    requestId,
                    sessionId,
                    statusCode,
                    elapsedMs,
                    code,
                    retryable
            );
            return new AiAnalysisClientException(
                    code,
                    "El servicio de análisis de voz rechazó la solicitud (HTTP " + statusCode + ").",
                    retryable
            );
        }
    }
}
