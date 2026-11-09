package com.oratoria.backend.billing;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class BillingExpiryScheduler {
    private static final Logger log=LoggerFactory.getLogger(BillingExpiryScheduler.class);
    private final JdbcTemplate jdbc;
    private final BillingService billing;
    public BillingExpiryScheduler(JdbcTemplate jdbc,BillingService billing) { this.jdbc=jdbc; this.billing=billing; }
    @Scheduled(initialDelay=60000,fixedDelay=60000)
    public void reconcile() {
        var users=jdbc.query("SELECT DISTINCT user_id FROM subscriptions WHERE demo_managed=true AND status='ACTIVE' AND ends_at<=CURRENT_TIMESTAMP LIMIT 100",(rs,row)->rs.getObject(1,UUID.class));
        for (UUID user:users) {
            try { billing.effectiveSubscription(user); }
            catch (RuntimeException failure) { log.warn("No se pudo conciliar una suscripción; se reintentará sin registrar contenido sensible."); }
        }
    }
}
