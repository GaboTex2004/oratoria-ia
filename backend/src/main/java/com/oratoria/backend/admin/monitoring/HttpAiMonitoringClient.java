package com.oratoria.backend.admin.monitoring;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class HttpAiMonitoringClient implements AiMonitoringClient {
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json;
    private final String url;
    private final String internalApiKey;

    public HttpAiMonitoringClient(ObjectMapper json,
                                  @Value("${app.ai-service.url:http://localhost:8000}") String baseUrl,
                                  @Value("${app.ai-service.internal-api-key:}") String internalApiKey) {
        this.json = json;
        this.url = baseUrl.replaceAll("/+$", "") + "/api/v1/monitoring/analyze";
        this.internalApiKey = internalApiKey == null ? "" : internalApiKey.trim();
    }

    @Override
    public Analysis analyze(Map<String, Object> metrics, List<MonitoringService.Alert> alerts, String question) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("metrics", metrics);
        body.put("alerts", alerts.stream().map(alert -> Map.of("code", alert.code(), "severity", alert.severity().name(),
                "message", alert.message())).toList());
        if (question != null && !question.isBlank()) body.put("question", question.strip());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(150))
                    .header("Content-Type", "application/json").header("X-Internal-Api-Key", internalApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = json.readTree(response.body());
            if (response.statusCode() != 200) {
                String code = root.path("code").asString("LOCAL_MODEL_ERROR");
                int status = response.statusCode();
                throw new AiMonitoringException(code, message(code, root.path("message").asString("")), status == 429 || status == 503 || status == 504);
            }
            List<Finding> findings = new ArrayList<>();
            for (JsonNode item : root.path("findings")) {
                List<String> evidence = new ArrayList<>();
                item.path("evidence").forEach(key -> evidence.add(key.asString()));
                findings.add(new Finding(item.path("title").asString(""), item.path("detail").asString(""), evidence));
            }
            List<String> recommendations = new ArrayList<>();
            root.path("recommendations").forEach(item -> recommendations.add(item.asString()));
            String answer = root.path("answer").isNull() || root.path("answer").isMissingNode() ? null : root.path("answer").asString();
            return new Analysis(root.path("status").asString("NORMAL"), root.path("summary").asString(""), findings,
                    recommendations, answer, root.path("summary_from_rules").asBoolean(false),
                    root.path("provider").asString("ollama"), root.path("model").asString(""));
        } catch (AiMonitoringException known) {
            throw known;
        } catch (HttpTimeoutException timeout) {
            throw new AiMonitoringException("LOCAL_MODEL_TIMEOUT", "El modelo local tardó demasiado en responder. Inténtalo de nuevo.", true);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiMonitoringException("INTERRUPTED", "La consulta al modelo local se interrumpió.", true);
        } catch (Exception unavailable) {
            throw new AiMonitoringException("AI_SERVICE_UNAVAILABLE", "El servicio de IA local no está disponible.", true);
        }
    }

    private static String message(String code, String fallback) {
        return switch (code) {
            case "LOCAL_MODEL_BUSY" -> "El modelo local está ocupado con una práctica. Inténtalo en unos segundos.";
            case "LOCAL_MODEL_MISSING" -> "Falta descargar el modelo local en Ollama.";
            case "LOCAL_RUNTIME_UNAVAILABLE" -> "Ollama no está en ejecución.";
            case "LOCAL_MODEL_TIMEOUT" -> "El modelo local tardó demasiado en responder. Inténtalo de nuevo.";
            default -> fallback.isBlank() ? "El modelo local no pudo generar el análisis." : fallback;
        };
    }
}
