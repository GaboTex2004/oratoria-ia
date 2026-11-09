package com.oratoria.backend.billing;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL persistence reuses the existing JPA plans/subscriptions, no card credentials. */
@Repository
public class BillingStore {
    private final JdbcTemplate jdbc;
    public BillingStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record PaymentOrder(UUID id, UUID userId, UUID subscriptionId, BigDecimal amount,
            String currency, String status, String kind, Instant createdAt, Instant periodStart, Instant periodEnd) {}
    public record PaymentTransaction(UUID id, UUID orderId, String scenario, String status,
            String authorizationCode, Instant createdAt, Instant resolvedAt) {}
    private static Instant instant(ResultSet rs, String field) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(field); return timestamp == null ? null : timestamp.toInstant();
    }
    private PaymentOrder mapOrder(ResultSet rs, int row) throws SQLException {
        return new PaymentOrder(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getObject("subscription_id", UUID.class), rs.getBigDecimal("amount"), rs.getString("currency"),
                rs.getString("status"), rs.getString("kind"), instant(rs,"created_at"), instant(rs,"period_start"), instant(rs,"period_end"));
    }
    private PaymentTransaction mapTransaction(ResultSet rs, int row) throws SQLException {
        return new PaymentTransaction(rs.getObject("id",UUID.class), rs.getObject("order_id",UUID.class),
                rs.getString("scenario"),rs.getString("status"),rs.getString("authorization_code"),
                instant(rs,"created_at"),instant(rs,"resolved_at"));
    }
    public Optional<PaymentOrder> order(UUID id) { return jdbc.query("SELECT * FROM payment_orders WHERE id=?", this::mapOrder, id).stream().findFirst(); }
    public Optional<PaymentOrder> byKey(UUID user, String key) {
        return jdbc.query("SELECT * FROM payment_orders WHERE user_id=? AND idempotency_key=?",this::mapOrder,user,key).stream().findFirst();
    }
    public Optional<PaymentTransaction> transactionFor(UUID order) {
        return jdbc.query("SELECT * FROM payment_transactions WHERE order_id=?",this::mapTransaction,order).stream().findFirst();
    }
    public Optional<PaymentTransaction> transaction(UUID id) {
        return jdbc.query("SELECT * FROM payment_transactions WHERE id=?",this::mapTransaction,id).stream().findFirst();
    }
    public List<PaymentOrder> history(UUID user, int page) {
        return jdbc.query("SELECT * FROM payment_orders WHERE user_id=? ORDER BY created_at DESC,id LIMIT 50 OFFSET ?",this::mapOrder,user,page*50);
    }
    public List<PaymentOrder> global(int page) {
        return jdbc.query("SELECT * FROM payment_orders ORDER BY created_at DESC,id LIMIT 50 OFFSET ?",this::mapOrder,page*50);
    }
    public void create(PaymentOrder order, String key) {
        jdbc.update("INSERT INTO payment_orders(id,user_id,subscription_id,amount,currency,status,kind,idempotency_key,created_at,period_start,period_end) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                order.id(),order.userId(),order.subscriptionId(),order.amount(),order.currency(),order.status(),order.kind(),key,
                Timestamp.from(order.createdAt()),ts(order.periodStart()),ts(order.periodEnd()));
    }
    private Timestamp ts(Instant value) { return value == null ? null : Timestamp.from(value); }
    public void record(PaymentOrder order, PaymentProvider.Scenario scenario, PaymentProvider.Result result, Instant now) {
        jdbc.update("INSERT INTO payment_transactions(id,order_id,scenario,status,authorization_code,created_at,resolved_at) VALUES(?,?,?,?,?,?,?)",
                result.transactionId(),order.id(),scenario.name(),result.status(),result.authorizationCode(),ts(now),result.status().equals("PENDING") ? null : ts(now));
        jdbc.update("UPDATE payment_orders SET status=? WHERE id=?",result.status(),order.id());
    }
    public void resolve(PaymentOrder order, String status, String authorization, Instant now) {
        jdbc.update("UPDATE payment_transactions SET status=?,authorization_code=?,resolved_at=? WHERE order_id=? AND status='PENDING'",status,authorization,ts(now),order.id());
        jdbc.update("UPDATE payment_orders SET status=? WHERE id=?",status,order.id());
    }
    public void event(UUID user, UUID actor, UUID order, String type, Instant now) {
        if (order != null) jdbc.update("INSERT INTO payment_events VALUES(?,?,?,?,?,?)",UUID.randomUUID(),order,type,actor,ts(now),"Simulación académica sin cargos reales");
        jdbc.update("INSERT INTO billing_audit_logs VALUES(?,?,?,?,?,?)",UUID.randomUUID(),user,actor,type,order,ts(now));
    }
    public List<Map<String,Object>> events(UUID order) {
        return jdbc.queryForList("SELECT event_type,created_at,detail FROM payment_events WHERE order_id=? ORDER BY created_at,id",order);
    }
    public Instant now(UUID user) {
        return jdbc.query("SELECT simulated_at FROM billing_demo_clocks WHERE user_id=?",(rs,row)->rs.getTimestamp(1).toInstant(),user)
                .stream().findFirst().filter(time->time.isAfter(Instant.now())).orElseGet(Instant::now);
    }
    public Instant advance(UUID user, int days) {
        Instant next = now(user).atZone(java.time.ZoneId.of("America/La_Paz")).plusDays(days).toInstant();
        jdbc.update("INSERT INTO billing_demo_clocks VALUES(?,?) ON CONFLICT(user_id) DO UPDATE SET simulated_at=EXCLUDED.simulated_at",user,ts(next));
        return next;
    }
    public void usage(UUID user, String kind) {
        jdbc.update("INSERT INTO usage_records VALUES(?,?,?,?)",UUID.randomUUID(),user,kind,ts(Instant.now()));
    }
}
