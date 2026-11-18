package com.oratoria.backend.subscription.service;

import com.oratoria.backend.subscription.entity.Feature;
import com.oratoria.backend.subscription.entity.PlanEntitlement;
import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.entity.SubscriptionStatus;
import com.oratoria.backend.subscription.entity.UsageLimit;
import com.oratoria.backend.subscription.repository.PlanEntitlementRepository;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FeatureAccessService {

    private final SubscriptionRepository subscriptions;
    private final PlanEntitlementRepository entitlements;
    private final com.oratoria.backend.billing.BillingService billing;

    public FeatureAccessService(SubscriptionRepository subscriptions,
                                PlanEntitlementRepository entitlements, com.oratoria.backend.billing.BillingService billing) {
        this.subscriptions = subscriptions;
        this.entitlements = entitlements;
        this.billing = billing;
    }

    public boolean hasAccess(UUID userId, Feature feature) {
        return activeSubscription(userId)
                .flatMap(subscription -> entitlements.findByPlanIdAndFeature(
                        subscription.getPlan().getId(), feature))
                .map(PlanEntitlement::isEnabled)
                .orElse(false);
    }

    public Optional<EntitlementLimit> limitFor(UUID userId, UsageLimit usageLimit) {
        return activeSubscription(userId)
                .flatMap(subscription -> entitlements.findByPlanIdAndUsageLimit(
                        subscription.getPlan().getId(), usageLimit))
                .filter(PlanEntitlement::isEnabled)
                .map(entitlement -> new EntitlementLimit(
                        entitlement.isUnlimited(), entitlement.getLimitValue()));
    }

    public void require(UUID userId, Feature feature) {
        if (!hasAccess(userId, feature)) throw new FeatureLockedException(feature);
    }

    public FeatureSummary summary(UUID userId) {
        Subscription subscription = billing.effectiveSubscription(userId);
        java.util.List<FeatureState> features = java.util.Arrays.stream(Feature.values())
                .map(feature -> new FeatureState(feature.name(), feature.label(), entitlements
                        .findByPlanIdAndFeature(subscription.getPlan().getId(), feature)
                        .map(PlanEntitlement::isEnabled).orElse(false)))
                .toList();
        return new FeatureSummary(subscription.getPlan().getCode().name(), features);
    }

    private Optional<Subscription> activeSubscription(UUID userId) {
        return Optional.of(billing.effectiveSubscription(userId));
    }

    public record FeatureSummary(String plan, java.util.List<FeatureState> features) {
    }

    public record FeatureState(String code, String label, boolean enabled) {
    }

    public record EntitlementLimit(boolean unlimited, Integer value) {
    }
}
