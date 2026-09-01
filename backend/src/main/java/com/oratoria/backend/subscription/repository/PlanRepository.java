package com.oratoria.backend.subscription.repository;

import com.oratoria.backend.subscription.entity.Plan;
import com.oratoria.backend.subscription.entity.PlanCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
    Optional<Plan> findByCodeAndActiveTrue(PlanCode code);
    Optional<Plan> findByCode(PlanCode code);
}
