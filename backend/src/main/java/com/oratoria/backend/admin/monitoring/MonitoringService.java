package com.oratoria.backend.admin.monitoring;

import com.oratoria.backend.admin.health.SystemHealthService;
import com.oratoria.backend.notification.NotificationService;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Estado en tiempo real de la plataforma. Todas las cifras y alertas salen de consultas y reglas fijas; la IA local
 * solo las interpreta después, a pedido (ver {@link MonitoringController}). No incluye datos personales.
 */
@Service
public class MonitoringService {
    static final int STUCK_MINUTES = 15;

    private final JdbcTemplate jdbc;
    private final SystemHealthService health;
    private final NotificationService notifications;

    public MonitoringService(JdbcTemplate jdbc, SystemHealthService health, NotificationService notifications) {
        this.jdbc = jdbc;
        this.health = health;
        this.notifications = notifications;
    }

    public enum Severity { NORMAL, ATENCION, CRITICO }

    public record Metric(String key, String label, Object value, String unit, String group) {}

    public record Alert(String code, Severity severity, String message) {}

    public record Event(Instant occurredAt, String action, String detail) {}

    public record Snapshot(Instant generatedAt, Severity status, List<Metric> metrics, List<Alert> alerts, List<Event> events) {
        /** Solo nombre → valor: es lo único que recibe el modelo local. */
        public Map<String, Object> values() {
            Map<String, Object> values = new LinkedHashMap<>();
            metrics.forEach(metric -> values.put(metric.key(), metric.value()));
            return values;
        }
    }

    public Snapshot snapshot() {
        List<Metric> metrics = new ArrayList<>();
        List<Alert> alerts = new ArrayList<>();

        for (SystemHealthService.ModuleStatus module : health.check()) {
            String key = switch (module.module()) {
                case "database" -> "servicio_base_datos";
                case "ai-service" -> "servicio_ia";
                case "audio-storage" -> "almacenamiento_audio";
                default -> "servicio_" + module.module().replace('-', '_');
            };
            String label = switch (module.module()) {
                case "database" -> "Base de datos";
                case "ai-service" -> "Servicio de IA local";
                case "audio-storage" -> "Almacenamiento de audio";
                default -> module.module();
            };
            metrics.add(new Metric(key, label, module.status(), null, "Servicios"));
            metrics.add(new Metric(key + "_latencia_ms", "Latencia: " + label, module.latencyMs(), "ms", "Servicios"));
            if (!"UP".equals(module.status())) alerts.add(new Alert(key.toUpperCase() + "_DOWN", Severity.CRITICO, label + " no responde."));
        }

        processing("speech_analyses", "analisis", "Análisis de voz", metrics, alerts);
        processing("speech_evaluations", "evaluaciones", "Evaluaciones", metrics, alerts);

        long practices = count("SELECT count(*) FROM training_sessions WHERE created_at > now() - interval '24 hours'");
        long activeUsers = count("SELECT count(DISTINCT user_id) FROM training_sessions WHERE created_at > now() - interval '24 hours'");
        long simulations = count("SELECT count(*) FROM simulation_sessions WHERE created_at > now() - interval '24 hours'");
        metrics.add(new Metric("practicas_24h", "Prácticas creadas (24 h)", practices, null, "Uso"));
        metrics.add(new Metric("usuarios_activos_24h", "Usuarios que practicaron (24 h)", activeUsers, null, "Uso"));
        metrics.add(new Metric("simulaciones_24h", "Simulaciones (24 h)", simulations, null, "Uso"));

        long failedLogins = count("SELECT count(*) FROM audit_log WHERE action = 'LOGIN_FAILED' AND occurred_at > now() - interval '1 hour'");
        metrics.add(new Metric("inicios_fallidos_1h", "Inicios de sesión fallidos (1 h)", failedLogins, null, "Seguridad"));
        if (failedLogins >= 10) alerts.add(new Alert("LOGIN_FAILURES", Severity.ATENCION,
                failedLogins + " inicios de sesión fallidos en la última hora: posible intento de adivinar contraseñas."));

        long deletions = count("SELECT count(*) FROM media_deletion_jobs");
        long oldestDeletion = count("SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)) / 60, 0)::bigint FROM media_deletion_jobs");
        metrics.add(new Metric("borrados_audio_pendientes", "Audios por borrar (privacidad)", deletions, null, "Privacidad"));
        metrics.add(new Metric("borrado_audio_mas_antiguo_min", "Borrado pendiente más antiguo", oldestDeletion, "min", "Privacidad"));
        if (oldestDeletion > 30) alerts.add(new Alert("MEDIA_DELETION_DELAYED", Severity.ATENCION,
                "Hay audios esperando borrado desde hace " + oldestDeletion + " minutos."));

        NotificationService.PushStats push = notifications.stats();
        metrics.add(new Metric("push_dispositivos", "Dispositivos con notificaciones", push.devices(), null, "Notificaciones"));
        metrics.add(new Metric("push_entregadas", "Notificaciones entregadas (desde el arranque)", push.delivered(), null, "Notificaciones"));
        metrics.add(new Metric("push_fallidas", "Notificaciones fallidas (desde el arranque)", push.failed(), null, "Notificaciones"));
        if (push.failed() >= 5 && push.failed() > push.delivered()) alerts.add(new Alert("PUSH_FAILING", Severity.ATENCION,
                "Fallan más notificaciones de las que se entregan: revisa las claves VAPID y PUSH_SUBJECT."));

        Runtime runtime = Runtime.getRuntime();
        long usedPct = Math.round(100.0 * (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory());
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 60000;
        metrics.add(new Metric("memoria_backend_pct", "Memoria usada por el backend", usedPct, "%", "Servidor"));
        metrics.add(new Metric("backend_activo_min", "Tiempo desde el arranque", uptime, "min", "Servidor"));
        if (usedPct >= 85) alerts.add(new Alert("BACKEND_MEMORY", Severity.ATENCION, "El backend usa el " + usedPct + " % de su memoria."));

        List<Event> events = jdbc.query("""
                SELECT occurred_at, action, detail FROM audit_log
                WHERE action IN ('LOGIN_FAILED', 'BACKUP_RESTORED', 'BACKUP_CREATED', 'SETTINGS_UPDATED', 'NOTIFICATION_BROADCAST', 'PLAN_UPDATED')
                ORDER BY occurred_at DESC LIMIT 8""",
                (rs, row) -> new Event(rs.getTimestamp(1).toInstant(), label(rs.getString(2)), rs.getString(3)));

        Severity status = alerts.stream().map(Alert::severity).max(Enum::compareTo).orElse(Severity.NORMAL);
        return new Snapshot(Instant.now(), status, metrics, alerts, events);
    }

    /** Cola de un trabajo asíncrono (análisis o evaluación): pendientes, en curso, trabados, fallidos y tiempo medio. */
    private void processing(String table, String prefix, String label, List<Metric> metrics, List<Alert> alerts) {
        long pending = count("SELECT count(*) FROM " + table + " WHERE status = 'PENDING'");
        long running = count("SELECT count(*) FROM " + table + " WHERE status = 'PROCESSING'");
        long stuck = count("SELECT count(*) FROM " + table + " WHERE status = 'PROCESSING' AND COALESCE(started_at, updated_at) < now() - interval '"
                + STUCK_MINUTES + " minutes'");
        long failed = count("SELECT count(*) FROM " + table + " WHERE status = 'FAILED' AND updated_at > now() - interval '1 hour'");
        long completed = count("SELECT count(*) FROM " + table + " WHERE status = 'COMPLETED' AND completed_at > now() - interval '1 hour'");
        long average = count("SELECT COALESCE(avg(EXTRACT(EPOCH FROM completed_at - started_at)), 0)::bigint FROM " + table
                + " WHERE status = 'COMPLETED' AND started_at IS NOT NULL AND completed_at > now() - interval '24 hours'");
        String group = label;
        metrics.add(new Metric(prefix + "_en_cola", label + " en cola", pending, null, group));
        metrics.add(new Metric(prefix + "_en_proceso", label + " en proceso", running, null, group));
        metrics.add(new Metric(prefix + "_trabados", label + " trabados (más de " + STUCK_MINUTES + " min)", stuck, null, group));
        metrics.add(new Metric(prefix + "_fallidos_1h", label + " fallidos (1 h)", failed, null, group));
        metrics.add(new Metric(prefix + "_completados_1h", label + " completados (1 h)", completed, null, group));
        metrics.add(new Metric(prefix + "_tiempo_medio_s", label + ": tiempo medio (24 h)", average, "s", group));
        if (stuck > 0) alerts.add(new Alert(prefix.toUpperCase() + "_STUCK", stuck >= 3 ? Severity.CRITICO : Severity.ATENCION,
                stuck + " " + label.toLowerCase() + " llevan más de " + STUCK_MINUTES + " minutos en proceso."));
        if (failed >= 3) alerts.add(new Alert(prefix.toUpperCase() + "_FAILING", Severity.ATENCION,
                failed + " " + label.toLowerCase() + " fallaron en la última hora."));
        if (pending > 10) alerts.add(new Alert(prefix.toUpperCase() + "_QUEUE", Severity.ATENCION,
                pending + " " + label.toLowerCase() + " esperan en cola."));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private static String label(String action) {
        try { return com.oratoria.backend.admin.audit.AuditAction.valueOf(action).label(); }
        catch (IllegalArgumentException unknown) { return action; }
    }
}
