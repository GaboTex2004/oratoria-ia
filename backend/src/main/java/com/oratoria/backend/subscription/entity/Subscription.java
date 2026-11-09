package com.oratoria.backend.subscription.entity;

import com.oratoria.backend.user.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt = Instant.now();

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;
    @Column(name = "grace_until")
    private Instant graceUntil;
    @Column(name = "renewal_scenario", nullable = false, length = 30)
    private String renewalScenario = "DEMO_APPROVED";
    @Column(name = "demo_managed", nullable = false)
    private boolean demoManaged;

    protected Subscription() {
    }

    public Subscription(UserAccount user, Plan plan) {
        this.user = user;
        this.plan = plan;
    }

    public UUID getId() { return id; }
    public void changePlan(Plan plan) {
        this.plan = plan; this.endsAt = null; this.graceUntil = null;
        this.demoManaged = false; this.autoRenew = false;
    }
    public UserAccount getUser() { return user; }
    public Plan getPlan() { return plan; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isAutoRenew() { return autoRenew; }
    public boolean isDemoManaged() { return demoManaged; }
    public Instant getGraceUntil() { return graceUntil; }
    public String getRenewalScenario() { return renewalScenario; }
    public void pending(Instant now) { status = SubscriptionStatus.PENDING_PAYMENT; startsAt = now; demoManaged = true; }
    public void activate(Instant start, Instant end) {
        status = SubscriptionStatus.ACTIVE; startsAt = start; endsAt = end;
        graceUntil = null; autoRenew = true; demoManaged = true;
    }
    public void renew(Instant end) { endsAt = end; graceUntil = null; }
    public void grace(Instant until) { graceUntil = until; }
    public void cancelRenewal() { autoRenew = false; }
    public void expire(boolean canceled) { status = canceled ? SubscriptionStatus.CANCELED : SubscriptionStatus.EXPIRED; autoRenew = false; }
    public void reject() { status = SubscriptionStatus.REJECTED; }
    public void renewalScenario(String scenario) { renewalScenario = scenario; }
}
