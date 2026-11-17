package com.oratoria.backend.admin.health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SystemHealthService {
    private static final Duration AI_TIMEOUT = Duration.ofSeconds(3);

    private final JdbcTemplate jdbc;
    private final String aiServiceUrl;
    private final Path audioDir;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(AI_TIMEOUT).build();

    public SystemHealthService(JdbcTemplate jdbc,
            @Value("${app.ai-service.url:http://localhost:8000}") String aiServiceUrl,
            @Value("${app.audio.storage-dir:./data/audio}") String audioDir) {
        this.jdbc = jdbc;
        this.aiServiceUrl = aiServiceUrl.replaceAll("/+$", "");
        this.audioDir = Paths.get(audioDir).toAbsolutePath().normalize();
    }

    public List<ModuleStatus> check() {
        return List.of(
                measure("database", () -> {
                    Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
                    return one != null && one == 1 ? "PostgreSQL responde." : null;
                }),
                measure("ai-service", () -> {
                    try {
                        HttpResponse<Void> response = http.send(HttpRequest.newBuilder(URI.create(aiServiceUrl + "/health"))
                                .timeout(AI_TIMEOUT).GET().build(), HttpResponse.BodyHandlers.discarding());
                        return response.statusCode() == 200 ? "Servicio de IA disponible." : null;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                }),
                measure("audio-storage", () -> {
                    try {
                        Files.createDirectories(audioDir);
                    } catch (Exception e) {
                        return null;
                    }
                    return Files.isWritable(audioDir) ? "Almacenamiento de audio escribible." : null;
                }));
    }

    /** A check returns a detail message when healthy, or null when the module is down. */
    private ModuleStatus measure(String module, Supplier<String> check) {
        long started = System.nanoTime();
        String detail;
        try {
            detail = check.get();
        } catch (RuntimeException e) {
            detail = null;
        }
        long latency = (System.nanoTime() - started) / 1_000_000;
        return detail != null ? new ModuleStatus(module, "UP", latency, detail)
                : new ModuleStatus(module, "DOWN", latency, "No responde o no está disponible.");
    }

    public record ModuleStatus(String module, String status, long latencyMs, String detail) {}
}
