package com.oratoria.backend.body;

import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.session.service.TrainingSessionService;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class BodyAnalysisService {
    private final TrainingSessionService sessions;
    private final TrainingSessionRepository sessionRepository;
    private final BodyAnalysisRepository results;
    private final ObjectMapper mapper;
    private final String url;
    private final String key;
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();

    public BodyAnalysisService(TrainingSessionService sessions, TrainingSessionRepository sessionRepository,
            BodyAnalysisRepository results, ObjectMapper mapper,
            @Value("${app.ai-service.url}") String url, @Value("${app.ai-service.internal-api-key}") String key) {
        this.sessions = sessions; this.sessionRepository = sessionRepository; this.results = results;
        this.mapper = mapper; this.url = url; this.key = key;
    }

    public JsonNode get(String email, UUID id) {
        sessions.getUserSession(email, id);
        return results.findBySessionId(id).map(result -> mapper.readTree(result.getResultJson()))
                .orElseThrow(() -> new ResourceNotFoundException("No hay análisis corporal para esta sesión."));
    }

    public JsonNode analyze(String email, UUID id, BodyController.BodyRequest payload) {
        sessions.getUserSession(email, id);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/api/v1/pose/analyze"))
                    .timeout(Duration.ofSeconds(90)).header("X-Internal-Api-Key", key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new BadRequestException("El análisis corporal local no está disponible. Revisa el servicio y su modelo.");
            JsonNode json = mapper.readTree(response.body());
            if (!json.has("framesAnalyzed")) throw new BadRequestException("Respuesta corporal incompleta.");
            BodyAnalysis result = results.findBySessionId(id).orElseGet(() ->
                    new BodyAnalysis(sessionRepository.getReferenceById(id), response.body()));
            result.updateResult(response.body());
            results.save(result);
            return json;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("El análisis corporal fue interrumpido.");
        } catch (java.io.IOException exception) {
            throw new BadRequestException("No se pudo conectar con el análisis corporal local.");
        }
    }
}
