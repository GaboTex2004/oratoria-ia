package com.oratoria.backend.notification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recordatorio diario para practicar: a partir de la hora elegida (en la zona horaria de la persona), una vez por día
 * y solo si ese día todavía no empezó ninguna práctica.
 */
@Component
public class DailyReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(DailyReminderScheduler.class);
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final Clock clock;

    @Autowired
    public DailyReminderScheduler(JdbcTemplate jdbc, NotificationService notifications) {
        this(jdbc, notifications, Clock.systemUTC());
    }

    DailyReminderScheduler(JdbcTemplate jdbc, NotificationService notifications, Clock clock) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.clock = clock;
    }

    private record Candidate(UUID userId, int hour, String zone, LocalDate lastSent) {}

    @Scheduled(initialDelay = 90000, fixedDelay = 300000)
    public void run() {
        var candidates = jdbc.query("""
                SELECT n.user_id, n.reminder_hour, n.time_zone, n.last_reminder_on FROM notification_settings n
                JOIN app_users u ON u.id = n.user_id
                WHERE n.daily_reminder = true AND u.enabled = true
                  AND EXISTS (SELECT 1 FROM push_subscriptions s WHERE s.user_id = n.user_id)""",
                (rs, row) -> new Candidate(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3),
                        rs.getObject(4, LocalDate.class)));
        int sent = 0;
        for (Candidate candidate : candidates) {
            try { if (remind(candidate)) sent++; }
            catch (RuntimeException failure) { log.warn("No se pudo procesar un recordatorio diario [userId={}]", candidate.userId()); }
        }
        if (sent > 0) log.info("Recordatorios diarios enviados [count={}]", sent);
    }

    private boolean remind(Candidate candidate) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(ZoneId.of(candidate.zone())));
        LocalDate today = now.toLocalDate();
        if (now.getHour() < candidate.hour() || today.equals(candidate.lastSent())) return false;
        Boolean practiced = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM training_sessions WHERE user_id = ? AND created_at >= ?)",
                Boolean.class, candidate.userId(), today.atStartOfDay(now.getZone()).toOffsetDateTime());
        // Se marca el día aunque ya haya practicado: no se insiste más tarde.
        int claimed = jdbc.update("""
                UPDATE notification_settings SET last_reminder_on = ?
                WHERE user_id = ? AND (last_reminder_on IS NULL OR last_reminder_on < ?)""", today, candidate.userId(), today);
        if (claimed == 0 || Boolean.TRUE.equals(practiced)) return false;
        notifications.sendToUser(candidate.userId(), new NotificationService.PushMessage("Hora de practicar",
                "Unos minutos hoy mantienen tu racha. ¿Hacemos una práctica corta?", "/training", "recordatorio"));
        return true;
    }
}
