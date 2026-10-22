package com.oratoria.backend.subscription.service;

import com.oratoria.backend.subscription.entity.Feature;
import com.oratoria.backend.subscription.entity.Plan;
import com.oratoria.backend.subscription.entity.PlanCode;
import com.oratoria.backend.subscription.entity.PlanEntitlement;
import com.oratoria.backend.subscription.entity.UsageLimit;
import com.oratoria.backend.subscription.repository.PlanEntitlementRepository;
import com.oratoria.backend.subscription.repository.PlanRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
public class PlanInitializationService implements ApplicationRunner {
    private static final Set<Feature> FREE_ENABLED_FEATURES = EnumSet.of(Feature.BASIC_VOICE_ANALYSIS);

    private final PlanRepository plans;
    private final PlanEntitlementRepository entitlements;

    public PlanInitializationService(PlanRepository plans, PlanEntitlementRepository entitlements) {
        this.plans = plans;
        this.entitlements = entitlements;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Plan free = ensurePlan(PlanCode.FREE, "Free");
        Plan premium = ensurePlan(PlanCode.PREMIUM, "Premium");
        initializeFreeEntitlements(free);
        initializePremiumEntitlements(premium);
    }

    @Transactional
    public Plan freePlan() {
        return plans.findByCodeAndActiveTrue(PlanCode.FREE)
                .orElseThrow(() -> new IllegalStateException("El plan FREE no está disponible."));
    }

    @Transactional
    public Plan premiumPlan() {
        return plans.findByCodeAndActiveTrue(PlanCode.PREMIUM)
                .orElseThrow(() -> new IllegalStateException("El plan PREMIUM no está disponible."));
    }

    private Plan ensurePlan(PlanCode code, String name) {
        return plans.findByCode(code).orElseGet(() -> plans.save(new Plan(code, name)));
    }

    private void initializeFreeEntitlements(Plan free) {
        for (Feature feature : Feature.values()) {
            ensureFeature(free, feature, FREE_ENABLED_FEATURES.contains(feature));
        }
        ensureLimit(free, UsageLimit.SESSIONS_PER_MONTH, 5, false);
        ensureLimit(free, UsageLimit.HISTORY_DAYS, 7, false);
        ensureLimit(free, UsageLimit.SIMULATIONS_PER_MONTH, 1, false);
    }

    private void initializePremiumEntitlements(Plan premium) {
        for (Feature feature : Feature.values()) {
            ensureFeature(premium, feature, true);
        }
        for (UsageLimit limit : UsageLimit.values()) {
            ensureLimit(premium, limit, null, true);
        }
    }

    private void ensureFeature(Plan plan, Feature feature, boolean enabled) {
        entitlements.findByPlanIdAndFeature(plan.getId(), feature)
                .orElseGet(() -> entitlements.save(PlanEntitlement.forFeature(plan, feature, enabled)));
    }

    private void ensureLimit(Plan plan, UsageLimit usageLimit, Integer limitValue, boolean unlimited) {
        entitlements.findByPlanIdAndUsageLimit(plan.getId(), usageLimit)
                .orElseGet(() -> entitlements.save(
                        PlanEntitlement.forLimit(plan, usageLimit, limitValue, unlimited)));
    }
}
