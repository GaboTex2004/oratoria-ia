package com.oratoria.backend.admin.audit;

import com.oratoria.backend.shared.exception.BadRequestException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    static final int PAGE_SIZE = 50;
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Writes in its own transaction so failed logins (whose request rolls back) are still kept. It only touches
     * audit_log, so it never waits on locks held by the caller, not even during a restore. Logging must never
     * break the audited action, so failures are reported and swallowed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actorEmail, AuditAction action, String entityType, String entityId, String detail, String ip) {
        try {
            jdbc.update("""
                    INSERT INTO audit_log (id, occurred_at, actor_email, action, entity_type, entity_id, detail, ip_address)
                    VALUES (?, now(), ?, ?, ?, ?, ?, ?)""",
                    UUID.randomUUID(), truncate(actorEmail, 320), action.name(), truncate(entityType, 60),
                    truncate(entityId, 100), truncate(detail, 500), truncate(ip, 64));
        } catch (RuntimeException exception) {
            log.error("No se pudo registrar la acción {} en la bitácora", action, exception);
        }
    }

    @Transactional(readOnly = true)
    public AuditPage search(String actor, String action, Instant from, Instant to, int page) {
        if (page < 0) throw new BadRequestException("Página inválida.");
        if (action != null && !action.isBlank() && Arrays.stream(AuditAction.values()).noneMatch(item -> item.name().equals(action))) {
            throw new BadRequestException("Acción de bitácora desconocida.");
        }
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (actor != null && !actor.isBlank()) { where.append(" AND lower(actor_email) = lower(?)"); args.add(actor.trim()); }
        if (action != null && !action.isBlank()) { where.append(" AND action = ?"); args.add(action); }
        if (from != null) { where.append(" AND occurred_at >= ?"); args.add(Timestamp.from(from)); }
        if (to != null) { where.append(" AND occurred_at < ?"); args.add(Timestamp.from(to)); }
        Long total = jdbc.queryForObject("SELECT count(*) FROM audit_log" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(PAGE_SIZE);
        pageArgs.add(page * PAGE_SIZE);
        List<AuditEntry> items = jdbc.query("SELECT * FROM audit_log" + where + " ORDER BY occurred_at DESC, id LIMIT ? OFFSET ?",
                (rs, i) -> {
                    AuditAction parsed = AuditAction.valueOf(rs.getString("action"));
                    return new AuditEntry(rs.getObject("id", UUID.class), rs.getTimestamp("occurred_at").toInstant(),
                            rs.getString("actor_email"), parsed.name(), parsed.label(), rs.getString("entity_type"),
                            rs.getString("entity_id"), rs.getString("detail"), rs.getString("ip_address"));
                }, pageArgs.toArray());
        return new AuditPage(items, total == null ? 0 : total, page, PAGE_SIZE);
    }

    public List<ActionOption> actions() {
        return Arrays.stream(AuditAction.values()).map(action -> new ActionOption(action.name(), action.label())).toList();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public record AuditEntry(UUID id, Instant occurredAt, String actorEmail, String action, String actionLabel,
                             String entityType, String entityId, String detail, String ipAddress) {}

    public record AuditPage(List<AuditEntry> items, long total, int page, int size) {}

    public record ActionOption(String code, String label) {}
}
