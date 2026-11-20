package com.oratoria.backend.admin.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Records every successful state-changing request under /api/admin, so new admin endpoints are audited by default. */
@Component
public class AdminAuditInterceptor implements HandlerInterceptor {
    /** Request attribute a controller can set when the affected id is only known after the action (e.g. a new backup). */
    public static final String ENTITY_ID = AdminAuditInterceptor.class.getName() + ".entityId";

    private record Rule(String method, Pattern path, AuditAction action, String entityType) {}

    private static final String ID = "([^/]+)";
    private static final List<Rule> RULES = List.of(
            new Rule("PUT", Pattern.compile("/api/admin/users/" + ID), AuditAction.USER_UPDATED, "user"),
            new Rule("POST", Pattern.compile("/api/admin/users/" + ID + "/password"), AuditAction.USER_PASSWORD_RESET, "user"),
            new Rule("POST", Pattern.compile("/api/admin/exercises"), AuditAction.EXERCISE_CREATED, "exercise"),
            new Rule("PUT", Pattern.compile("/api/admin/exercises/" + ID), AuditAction.EXERCISE_UPDATED, "exercise"),
            new Rule("POST", Pattern.compile("/api/admin/categories"), AuditAction.CATEGORY_CREATED, "category"),
            new Rule("PUT", Pattern.compile("/api/admin/categories/" + ID), AuditAction.CATEGORY_UPDATED, "category"),
            new Rule("PUT", Pattern.compile("/api/admin/plans/" + ID), AuditAction.PLAN_UPDATED, "plan"),
            new Rule("PUT", Pattern.compile("/api/admin/settings"), AuditAction.SETTINGS_UPDATED, "settings"),
            new Rule("POST", Pattern.compile("/api/admin/payments/orders/" + ID + "/confirm"), AuditAction.PAYMENT_CONFIRMED, "payment"),
            new Rule("POST", Pattern.compile("/api/admin/payments/users/" + ID + "/advance"), AuditAction.BILLING_CLOCK_ADVANCED, "user"),
            new Rule("POST", Pattern.compile("/api/admin/backups"), AuditAction.BACKUP_CREATED, "backup"),
            new Rule("POST", Pattern.compile("/api/admin/backups/restore"), AuditAction.BACKUP_RESTORED, "backup"),
            new Rule("POST", Pattern.compile("/api/admin/backups/" + ID + "/restore"), AuditAction.BACKUP_RESTORED, "backup"),
            new Rule("DELETE", Pattern.compile("/api/admin/backups/" + ID), AuditAction.BACKUP_DELETED, "backup"),
            new Rule("POST", Pattern.compile("/api/admin/notifications/broadcast"), AuditAction.NOTIFICATION_BROADCAST, "notification"));

    private final AuditService audit;

    public AdminAuditInterceptor(AuditService audit) { this.audit = audit; }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) return;
        if (!path.startsWith("/api/admin/") || response.getStatus() >= 400 || exception != null) return;
        Principal principal = request.getUserPrincipal();
        AuditAction action = AuditAction.ADMIN_ACTION;
        String entityType = null;
        String entityId = null;
        for (Rule rule : RULES) {
            Matcher matcher = rule.path().matcher(path);
            if (rule.method().equals(method) && matcher.matches()) {
                action = rule.action();
                entityType = rule.entityType();
                entityId = matcher.groupCount() > 0 ? matcher.group(1) : null;
                break;
            }
        }
        Object explicitId = request.getAttribute(ENTITY_ID);
        if (explicitId != null) entityId = explicitId.toString();
        audit.record(principal == null ? null : principal.getName(), action, entityType, entityId,
                method + " " + path, request.getRemoteAddr());
    }
}
