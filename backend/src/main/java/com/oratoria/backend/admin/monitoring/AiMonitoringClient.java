package com.oratoria.backend.admin.monitoring;

import java.util.List;
import java.util.Map;

/** Diagnóstico redactado por el modelo local del ai-service (Qwen vía Ollama). Se reemplaza en pruebas. */
public interface AiMonitoringClient {
    record Finding(String title, String detail, List<String> evidence) {}

    /** summaryFromRules: el resumen del modelo contradecía las alertas y se reemplazó por uno armado con ellas. */
    record Analysis(String status, String summary, List<Finding> findings, List<String> recommendations, String answer,
                    boolean summaryFromRules, String provider, String model) {}

    Analysis analyze(Map<String, Object> metrics, List<MonitoringService.Alert> alerts, String question);

    /** Error explícito del modelo local: ocupado, caído o respuesta inválida. Nunca se reemplaza por texto inventado. */
    class AiMonitoringException extends RuntimeException {
        private final String code;
        private final boolean retryable;

        public AiMonitoringException(String code, String message, boolean retryable) {
            super(message);
            this.code = code;
            this.retryable = retryable;
        }

        public String code() { return code; }

        public boolean retryable() { return retryable; }
    }
}
