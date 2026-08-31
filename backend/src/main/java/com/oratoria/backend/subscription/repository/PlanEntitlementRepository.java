package com.oratoria.backend.subscription.repository;

import com.oratoria.backend.subscription.entity.Feature;
import com.oratoria.backend.subscription.entity.PlanEntitlement;
import com.oratoria.backend.subscription.entity.UsageLimit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanEntitlementRepository extends JpaRepository<PlanEntitlement, UUID> {
    Optional<PlanEntitlement> findByPlanIdAndFeature(UUID planId, Feature feature);
    Optional<PlanEntitlement> findByPlanIdAndUsageLimit(UUID planId, UsageLimit usageLimit);
}
