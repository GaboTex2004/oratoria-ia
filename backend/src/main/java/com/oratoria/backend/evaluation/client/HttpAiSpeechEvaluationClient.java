package com.oratoria.backend.evaluation.client;

import com.oratoria.backend.analysis.dto.VerbalMetricsDto;
import com.oratoria.backend.training.TrainingType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class HttpAiSpeechEvaluationClient implements AiSpeechEvaluationClient {
    private static final Logger log = LoggerFactory.getLogger(HttpAiSpeechEvaluationClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String evaluateUrl;
    private final String internalApiKey;
    private final Duration requestTimeout;

    public HttpAiSpeechEvaluationClient(
            ObjectMapper objectMapper,
            @Value("${app.ai-service.url:http://localhost:8000}") String baseUrl,
            @Value("${app.ai-service.internal-api-key:dev-internal-oratoria-key-change-in-prod}") String internalApiKey,
            @Value("${app.ai-service.timeout-seconds:65}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.evaluateUrl = cleanBase + "/api/v1/evaluation/evaluate";
        this.internalApiKey = internalApiKey != null ? internalApiKey.trim() : "";
        this.requestTimeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public AiSpeechEvaluationPayload evaluateSpeech(
            UUID sessionId,
            TrainingType trainingType,
            String exerciseTitle,
            String exercisePrompt,
            String transcriptText,
            VerbalMetricsDto metrics) {
        String requestId = UUID.randomUUID().toString();
        AiSpeechEvaluationRequest reqPayload = new AiSpeechEvaluationRequest(
                sessionId.toString(),
                trainingType != null ? trainingType.name() : "PRESENTATION",
                exerciseTitle,
                exercisePrompt,
                transcriptText != null ? transcriptText : "",
                metrics
        );

        byte[] jsonBytes = objectMapper.writeValueAsString(reqPayload).getBytes(StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(evaluateUrl))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(requestTimeout)
                .header("X-Internal-Api-Key", internalApiKey)
                .header("X-Request-Id", requestId)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes))
                .build();

        long startNanos = System.nanoTime();
        log.info(
                "Sending speech evaluation request [requestId={} sessionId={} type={} words={}]",
                requestId,
                sessionId,
                reqPayload.trainingType(),
                metrics != null ? metrics.wordCount() : 0
        );

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            int status = response.statusCode();
            String respReqId = response.headers().firstValue("X-Request-Id").orElse(requestId);

            if (status >= 200 && status < 300) {
                final AiSpeechEvaluationPayload payload;
                try {
                    payload = objectMapper.readValue(
                            response.body(),
                            AiSpeechEvaluationPayload.class
                    );
                } catch (Exception parseEx) {
                    log.warn(
                            "Speech evaluation returned invalid JSON [requestId={} sessionId={} status={} elapsedMs={} code=INVALID_PROVIDER_RESPONSE]",
                            respReqId,
                            sessionId,
                            status,
                            elapsedMs
                    );
                    throw new AiEvaluationClientException(
                            "INVALID_PROVIDER_RESPONSE",
                            "El servicio de evaluación devolvió un formato JSON inválido.",
                            true
                    );
                }
                if (payload == null || payload.rubricVersion() == null || payload.executiveSummary() == null) {
                    log.warn(
                            "Speech evaluation returned incomplete payload [requestId={} sessionId={} status={} elapsedMs={} code=INVALID_PROVIDER_RESPONSE]",
                            respReqId,
                            sessionId,
                            status,
                            elapsedMs
                    );
                    throw new AiEvaluationClientException(
                            "INVALID_PROVIDER_RESPONSE",
                            "El servicio de evaluación devolvió una respuesta incompleta.",
                            true
                    );
                }
                log.info(
                        "Speech evaluation completed [requestId={} sessionId={} status={} elapsedMs={} rubric={} overallScore={}]",
                        respReqId,
                        sessionId,
                        status,
                        elapsedMs,
                        payload.rubricVersion(),
                        payload.overallScore()
                );
                return payload;
            }
            throw parseErrorResponse(sessionId, respReqId, status, elapsedMs, response.body());
        } catch (HttpTimeoutException timeoutException) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.warn(
                    "Speech evaluation timed out [requestId={} sessionId={} elapsedMs={} code=EVALUATION_SERVICE_TIMEOUT]",
                    requestId,
                    sessionId,
                    elapsedMs
            );
            throw new AiEvaluationClientException(
                    "EVALUATION_SERVICE_TIMEOUT",
                    "El motor de evaluación discursiva agotó el tiempo de espera.",
                    true
            );
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AiEvaluationClientException(
                    "EVALUATION_SERVICE_INTERRUPTED",
                    "La evaluación del discurso fue interrumpida.",
                    true
            );
        } catch (IOException ioException) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.warn(
                    "Speech evaluation service unavailable [requestId={} sessionId={} elapsedMs={} reason={}]",
                    requestId,
                    sessionId,
                    elapsedMs,
                    ioException.getClass().getSimpleName()
            );
            throw new AiEvaluationClientException(
                    "EVALUATION_SERVICE_UNAVAILABLE",
                    "El servicio de evaluación con IA no está disponible en este momento.",
                    true
            );
        }
    }

    private AiEvaluationClientException parseErrorResponse(
            UUID sessionId,
            String requestId,
            int statusCode,
            long elapsedMs,
            String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String code = node.hasNonNull("code") ? node.get("code").asText() : "EVALUATION_ERROR_" + statusCode;
            String message = node.hasNonNull("message")
                    ? node.get("message").asText()
                    : "Error en el motor de evaluación discursiva (HTTP " + statusCode + ").";
            String origin = node.hasNonNull("origin") ? node.get("origin").asText() : "ai-service";
            String upstreamReqId = node.hasNonNull("requestId") ? node.get("requestId").asText() : requestId;
            boolean retryable = node.hasNonNull("retryable")
                    ? node.get("retryable").asBoolean()
                    : (statusCode == 429 || statusCode >= 500);

            log.warn(
                    "Speech evaluation failed [requestId={} sessionId={} status={} elapsedMs={} origin={} code={} retryable={}]",
                    upstreamReqId,
                    sessionId,
                    statusCode,
                    elapsedMs,
                    origin,
                    code,
                    retryable
            );
            return new AiEvaluationClientException(code, message, retryable);
        } catch (Exception ignored) {
            boolean retryable = statusCode == 429 || statusCode >= 500;
            String code = statusCode == 401 || statusCode == 403
                    ? "UNAUTHORIZED_INTERNAL_CALL"
                    : "EVALUATION_HTTP_" + statusCode;
            return new AiEvaluationClientException(
                    code,
                    "El servicio de evaluación rechazó la solicitud (HTTP " + statusCode + ").",
                    retryable
            );
        }
    }
}
