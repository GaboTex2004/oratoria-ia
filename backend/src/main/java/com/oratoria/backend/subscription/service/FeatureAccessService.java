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
@Transactional(readOnly = true)
public class FeatureAccessService {

    private final SubscriptionRepository subscriptions;
    private final PlanEntitlementRepository entitlements;

    public FeatureAccessService(SubscriptionRepository subscriptions,
                                PlanEntitlementRepository entitlements) {
        this.subscriptions = subscriptions;
        this.entitlements = entitlements;
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

    private Optional<Subscription> activeSubscription(UUID userId) {
        return subscriptions.findFirstByUserIdAndStatusOrderByStartsAtDesc(
                userId, SubscriptionStatus.ACTIVE);
    }

    public record EntitlementLimit(boolean unlimited, Integer value) {
    }
}
