package com.oratoria.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.oratoria.backend.subscription.entity.Feature;
import com.oratoria.backend.subscription.entity.Plan;
import com.oratoria.backend.subscription.entity.PlanCode;
import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.entity.UsageLimit;
import com.oratoria.backend.subscription.repository.PlanEntitlementRepository;
import com.oratoria.backend.subscription.repository.PlanRepository;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.FeatureAccessService;
import com.oratoria.backend.subscription.service.FeatureAccessService.EntitlementLimit;
import com.oratoria.backend.subscription.service.PlanInitializationService;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import com.oratoria.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SubscriptionIntegrationTests {
    @Autowired PlanInitializationService planInitializer;
    @Autowired PlanRepository plans;
    @Autowired PlanEntitlementRepository entitlements;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired UserAccountRepository users;
    @Autowired FeatureAccessService featureAccess;

    @Test
    void plansAndEntitlementsAreInitializedIdempotently() {
        long initialPlanCount = plans.count();
        long initialEntitlementCount = entitlements.count();

        planInitializer.run(new DefaultApplicationArguments());

        assertThat(plans.findByCodeAndActiveTrue(PlanCode.FREE)).isPresent();
        assertThat(plans.findByCodeAndActiveTrue(PlanCode.PREMIUM)).isPresent();
        assertThat(plans.count()).isEqualTo(initialPlanCount);
        assertThat(entitlements.count()).isEqualTo(initialEntitlementCount);
    }

    @Test
    void freePlanGrantsBasicVoiceAnalysisAndFiniteUsageLimits() {
        UserAccount freeUser = users.save(new UserAccount(
                "Free", "User", "free-plan@example.test", "$2a$10$hash", UserRole.USER));
        subscriptions.save(new Subscription(freeUser, planInitializer.freePlan()));

        assertThat(featureAccess.hasAccess(freeUser.getId(), Feature.BASIC_VOICE_ANALYSIS)).isTrue();
        assertThat(featureAccess.hasAccess(freeUser.getId(), Feature.ADVANCED_VOICE_ANALYSIS)).isFalse();
        assertThat(featureAccess.hasAccess(freeUser.getId(), Feature.CAMERA_ANALYSIS)).isFalse();
        assertThat(featureAccess.hasAccess(freeUser.getId(), Feature.UNLIMITED_SIMULATIONS)).isFalse();

        assertThat(featureAccess.limitFor(freeUser.getId(), UsageLimit.SESSIONS_PER_MONTH))
                .contains(new EntitlementLimit(false, 5));
        assertThat(featureAccess.limitFor(freeUser.getId(), UsageLimit.HISTORY_DAYS))
                .contains(new EntitlementLimit(false, 7));
        assertThat(featureAccess.limitFor(freeUser.getId(), UsageLimit.SIMULATIONS_PER_MONTH))
                .contains(new EntitlementLimit(false, 1));
    }

    @Test
    void premiumPlanGrantsAllFeaturesAndUnlimitedQuotas() {
        Plan premium = planInitializer.premiumPlan();
        UserAccount premiumUser = users.save(new UserAccount(
                "Premium", "User", "premium-plan@example.test", "$2a$10$hash", UserRole.USER));
        subscriptions.save(new Subscription(premiumUser, premium));

        for (Feature feature : Feature.values()) {
            assertThat(featureAccess.hasAccess(premiumUser.getId(), feature))
                    .as("Feature %s should be enabled for PREMIUM", feature)
                    .isTrue();
        }
        for (UsageLimit limit : UsageLimit.values()) {
            assertThat(featureAccess.limitFor(premiumUser.getId(), limit))
                    .as("UsageLimit %s should be unlimited for PREMIUM", limit)
                    .contains(new EntitlementLimit(true, null));
        }
    }
}
