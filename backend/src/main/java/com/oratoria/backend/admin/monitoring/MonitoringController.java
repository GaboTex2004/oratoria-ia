package com.oratoria.backend.admin.monitoring;

import com.oratoria.backend.admin.monitoring.AiMonitoringClient.AiMonitoringException;
import com.oratoria.backend.shared.exception.BadRequestException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Monitoreo en tiempo real (solo ADMIN, por estar bajo /api/admin) y asistente de diagnóstico con la IA local. */
@RestController
@RequestMapping("/api/admin/monitoring")
public class MonitoringController {
    private final MonitoringService monitoring;
    private final AiMonitoringClient ai;

    public MonitoringController(MonitoringService monitoring, AiMonitoringClient ai) {
        this.monitoring = monitoring;
        this.ai = ai;
    }

    public record AnalysisRequest(String question) {}

    public record AnalysisResponse(Instant snapshotAt, MonitoringService.Severity ruleStatus, AiMonitoringClient.Analysis analysis,
                                   Map<String, Object> metricsUsed) {}

    @GetMapping
    MonitoringService.Snapshot snapshot() { return monitoring.snapshot(); }

    /** Toma un estado nuevo y se lo pasa al modelo local. Se ejecuta solo a pedido para no competir con las prácticas. */
    @PostMapping("/analysis")
    AnalysisResponse analyze(@RequestBody(required = false) AnalysisRequest request) {
        String question = request == null || request.question() == null ? null : request.question().strip();
        if (question != null && question.length() > 300) throw new BadRequestException("La pregunta admite hasta 300 caracteres.");
        MonitoringService.Snapshot snapshot = monitoring.snapshot();
        AiMonitoringClient.Analysis analysis = ai.analyze(snapshot.values(), snapshot.alerts(), question == null || question.isEmpty() ? null : question);
        return new AnalysisResponse(snapshot.generatedAt(), snapshot.status(), analysis, snapshot.values());
    }

    @ExceptionHandler(AiMonitoringException.class)
    ResponseEntity<Map<String, Object>> modelUnavailable(AiMonitoringException failure) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", failure.code(), "message", failure.getMessage(), "retryable", failure.retryable()));
    }
}
