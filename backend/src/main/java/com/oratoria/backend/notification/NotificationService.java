package com.oratoria.backend.notification;

import com.oratoria.backend.notification.PushGateway.Delivery;
import com.oratoria.backend.notification.PushGateway.Target;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationService {
    static final int MAX_FAILURES = 5;

    private final JdbcTemplate jdbc;
    private final UserAccountRepository users;
    private final PushGateway gateway;
    private final VapidKeyService vapid;
    private final ObjectMapper json;
    private final List<String> allowedHosts;

    public NotificationService(JdbcTemplate jdbc, UserAccountRepository users, PushGateway gateway, VapidKeyService vapid,
                               ObjectMapper json, @Value("${app.push.allowed-hosts}") String allowedHosts) {
        this.jdbc = jdbc;
        this.users = users;
        this.gateway = gateway;
        this.vapid = vapid;
        this.json = json;
        this.allowedHosts = Arrays.stream(allowedHosts.split(",")).map(String::trim).filter(host -> !host.isEmpty()).toList();
    }

    public record PushMessage(String title, String body, String url, String tag) {}

    public record SendSummary(int devices, int delivered, int removed, int failed) {}

    public record SettingsView(boolean practiceReady, boolean dailyReminder, int reminderHour, String timeZone, int devices) {}

    public record SettingsUpdate(boolean practiceReady, boolean dailyReminder, int reminderHour, String timeZone) {}

    public String publicKey() { return vapid.keys().publicKey(); }

    public void subscribe(String email, String endpoint, String p256dh, String auth, String userAgent) {
        validateEndpoint(endpoint);
        try {
            byte[] key = WebPushCrypto.decode(p256dh);
            byte[] secret = WebPushCrypto.decode(auth);
            if (key.length != 65 || key[0] != 4 || secret.length != 16) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("Las claves de la suscripción no son válidas.");
        }
        UUID userId = userId(email);
        String agent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 300));
        // El mismo navegador puede pasar a otra cuenta: el endpoint queda con el último usuario que lo registró.
        jdbc.update("""
                INSERT INTO push_subscriptions (id, user_id, endpoint, p256dh, auth_secret, user_agent)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (endpoint) DO UPDATE SET user_id = EXCLUDED.user_id, p256dh = EXCLUDED.p256dh,
                    auth_secret = EXCLUDED.auth_secret, user_agent = EXCLUDED.user_agent, failure_count = 0""",
                UUID.randomUUID(), userId, endpoint, p256dh, auth, agent);
    }

    public boolean isRegistered(String email, String endpoint) {
        Boolean registered = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM push_subscriptions WHERE endpoint = ? AND user_id = ?)",
                Boolean.class, endpoint, userId(email));
        return Boolean.TRUE.equals(registered);
    }

    public void unsubscribe(String email, String endpoint) {
        jdbc.update("DELETE FROM push_subscriptions WHERE endpoint = ? AND user_id = ?", endpoint, userId(email));
    }

    public SettingsView settings(String email) {
        UUID userId = userId(email);
        int devices = devices(userId);
        var rows = jdbc.query("SELECT practice_ready, daily_reminder, reminder_hour, time_zone FROM notification_settings WHERE user_id = ?",
                (rs, row) -> new SettingsView(rs.getBoolean(1), rs.getBoolean(2), rs.getInt(3), rs.getString(4), devices), userId);
        return rows.isEmpty() ? new SettingsView(true, false, 19, "America/La_Paz", devices) : rows.getFirst();
    }

    public SettingsView updateSettings(String email, SettingsUpdate update) {
        if (update.reminderHour() < 0 || update.reminderHour() > 23) throw new BadRequestException("La hora del recordatorio debe estar entre 0 y 23.");
        String zone = update.timeZone() == null || update.timeZone().isBlank() ? "America/La_Paz" : update.timeZone().trim();
        try { ZoneId.of(zone); } catch (DateTimeException invalid) { throw new BadRequestException("Zona horaria no válida."); }
        UUID userId = userId(email);
        // Cambiar la hora permite recibir el recordatorio otra vez hoy si la nueva hora todavía no pasó.
        jdbc.update("""
                INSERT INTO notification_settings (user_id, practice_ready, daily_reminder, reminder_hour, time_zone, updated_at)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (user_id) DO UPDATE SET practice_ready = EXCLUDED.practice_ready, daily_reminder = EXCLUDED.daily_reminder,
                    last_reminder_on = CASE WHEN notification_settings.reminder_hour <> EXCLUDED.reminder_hour
                        OR notification_settings.time_zone <> EXCLUDED.time_zone THEN NULL ELSE notification_settings.last_reminder_on END,
                    reminder_hour = EXCLUDED.reminder_hour, time_zone = EXCLUDED.time_zone, updated_at = now()""",
                userId, update.practiceReady(), update.dailyReminder(), update.reminderHour(), zone);
        return settings(email);
    }

    public SendSummary sendTest(String email) {
        UUID userId = userId(email);
        if (devices(userId) == 0) throw new BadRequestException("Activa las notificaciones en este dispositivo antes de enviar una prueba.");
        return sendToUser(userId, new PushMessage("OratoriaAI", "¡Listo! Las notificaciones funcionan en este dispositivo.", "/home", "prueba"));
    }

    public SendSummary broadcast(String title, String body) {
        String cleanTitle = clean(title, 60, "El título");
        String cleanBody = clean(body, 200, "El mensaje");
        List<UUID> recipients = jdbc.queryForList("""
                SELECT DISTINCT s.user_id FROM push_subscriptions s JOIN app_users u ON u.id = s.user_id WHERE u.enabled = true""", UUID.class);
        SendSummary total = new SendSummary(0, 0, 0, 0);
        for (UUID userId : recipients) total = add(total, sendToUser(userId, new PushMessage(cleanTitle, cleanBody, "/home", "aviso")));
        return total;
    }

    /** Aviso cuando la evaluación de una práctica terminó, si la persona lo tiene activado. */
    public void notifyPracticeReady(UUID sessionId) {
        var rows = jdbc.query("""
                SELECT t.user_id, e.title FROM training_sessions t
                JOIN app_users u ON u.id = t.user_id
                LEFT JOIN exercises e ON e.id = t.exercise_id
                LEFT JOIN notification_settings n ON n.user_id = t.user_id
                WHERE t.id = ? AND u.enabled = true AND COALESCE(n.practice_ready, true) = true""",
                (rs, row) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2) == null ? "tu práctica" : "«" + rs.getString(2) + "»"), sessionId);
        if (rows.isEmpty()) return;
        var row = rows.getFirst();
        sendToUser(row.getKey(), new PushMessage("Tu resultado está listo",
                "Ya puedes ver la evaluación de " + row.getValue() + ".", "/reports/" + sessionId, "resultado-" + sessionId));
    }

    SendSummary sendToUser(UUID userId, PushMessage message) {
        List<Map.Entry<UUID, Target>> targets = jdbc.query("SELECT id, endpoint, p256dh, auth_secret FROM push_subscriptions WHERE user_id = ?",
                (rs, row) -> Map.entry(rs.getObject(1, UUID.class), new Target(rs.getString(2), rs.getString(3), rs.getString(4))), userId);
        byte[] payload = serialize(message);
        int delivered = 0, removed = 0, failed = 0;
        for (var target : targets) {
            Delivery result = gateway.send(target.getValue(), payload);
            switch (result) {
                case DELIVERED -> {
                    delivered++;
                    jdbc.update("UPDATE push_subscriptions SET last_success_at = now(), failure_count = 0 WHERE id = ?", target.getKey());
                }
                case GONE -> {
                    removed++;
                    jdbc.update("DELETE FROM push_subscriptions WHERE id = ?", target.getKey());
                }
                case FAILED -> {
                    failed++;
                    jdbc.update("UPDATE push_subscriptions SET failure_count = failure_count + 1 WHERE id = ?", target.getKey());
                    jdbc.update("DELETE FROM push_subscriptions WHERE id = ? AND failure_count >= ?", target.getKey(), MAX_FAILURES);
                }
            }
        }
        return new SendSummary(targets.size(), delivered, removed, failed);
    }

    private byte[] serialize(PushMessage message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("title", message.title());
        body.put("body", message.body());
        body.put("url", message.url());
        body.put("tag", message.tag());
        return json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
    }

    /** Solo servicios push conocidos: evita que el servidor haga peticiones a direcciones arbitrarias (SSRF). */
    void validateEndpoint(String endpoint) {
        URI uri;
        try { uri = URI.create(endpoint == null ? "" : endpoint.trim()); }
        catch (IllegalArgumentException invalid) { throw new BadRequestException("El destino de la suscripción no es válido."); }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || endpoint.length() > 1000 || uri.getUserInfo() != null
                || allowedHosts.stream().noneMatch(pattern -> matches(host, pattern.toLowerCase()))) {
            throw new BadRequestException("El destino de la suscripción no corresponde a un servicio push permitido.");
        }
    }

    private static boolean matches(String host, String pattern) {
        return pattern.startsWith("*.") ? host.endsWith(pattern.substring(1)) : host.equals(pattern);
    }

    private int devices(UUID userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM push_subscriptions WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private UUID userId(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado.")).getId();
    }

    private static String clean(String value, int max, String field) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > max) throw new BadRequestException(field + " es obligatorio y admite hasta " + max + " caracteres.");
        return text;
    }

    private static SendSummary add(SendSummary a, SendSummary b) {
        return new SendSummary(a.devices() + b.devices(), a.delivered() + b.delivered(), a.removed() + b.removed(), a.failed() + b.failed());
    }
}
