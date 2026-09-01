package com.oratoria.backend.subscription.service;

import com.oratoria.backend.subscription.entity.Plan;
import com.oratoria.backend.subscription.entity.PlanCode;
import com.oratoria.backend.subscription.repository.PlanRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
public class PlanInitializationService implements ApplicationRunner {
    private final PlanRepository plans;

    public PlanInitializationService(PlanRepository plans) { this.plans = plans; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        plans.findByCode(PlanCode.FREE).orElseGet(() -> plans.save(new Plan(PlanCode.FREE, "Free")));
    }

    @Transactional
    public Plan freePlan() {
        return plans.findByCodeAndActiveTrue(PlanCode.FREE)
                .orElseThrow(() -> new IllegalStateException("El plan FREE no está disponible."));
    }
}
