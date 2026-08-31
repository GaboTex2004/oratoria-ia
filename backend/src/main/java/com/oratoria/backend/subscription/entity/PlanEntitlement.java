package com.oratoria.backend.subscription.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "plan_entitlements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_plan_entitlement_feature", columnNames = {"plan_id", "feature"}),
        @UniqueConstraint(name = "uk_plan_entitlement_limit", columnNames = {"plan_id", "usage_limit"})
})
public class PlanEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(length = 60)
    private Feature feature;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_limit", length = 60)
    private UsageLimit usageLimit;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "limit_value")
    private Integer limitValue;

    @Column(nullable = false)
    private boolean unlimited;

    protected PlanEntitlement() {
    }

    public static PlanEntitlement forFeature(Plan plan, Feature feature, boolean enabled) {
        PlanEntitlement entitlement = new PlanEntitlement();
        entitlement.plan = plan;
        entitlement.feature = feature;
        entitlement.enabled = enabled;
        return entitlement;
    }

    public static PlanEntitlement forLimit(Plan plan, UsageLimit usageLimit,
                                           Integer limitValue, boolean unlimited) {
        if (!unlimited && (limitValue == null || limitValue < 0)) {
            throw new IllegalArgumentException("A finite entitlement requires a non-negative limit");
        }
        PlanEntitlement entitlement = new PlanEntitlement();
        entitlement.plan = plan;
        entitlement.usageLimit = usageLimit;
        entitlement.enabled = true;
        entitlement.limitValue = unlimited ? null : limitValue;
        entitlement.unlimited = unlimited;
        return entitlement;
    }

    public UUID getId() { return id; }
    public Plan getPlan() { return plan; }
    public Feature getFeature() { return feature; }
    public UsageLimit getUsageLimit() { return usageLimit; }
    public boolean isEnabled() { return enabled; }
    public Integer getLimitValue() { return limitValue; }
    public boolean isUnlimited() { return unlimited; }
}
