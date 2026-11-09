package com.oratoria.backend.subscription.service;

import com.oratoria.backend.subscription.repository.PlanRepository;
import com.oratoria.backend.subscription.repository.PlanEntitlementRepository;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/plans")
public class PlanController {
    private final PlanRepository plans;
    private final PlanEntitlementRepository entitlements;
    public PlanController(PlanRepository plans, PlanEntitlementRepository entitlements) {
        this.plans = plans; this.entitlements = entitlements;
    }
    @GetMapping
    @Transactional(readOnly = true)
    public List<PlanView> list() {
        return plans.findAll().stream().filter(plan -> plan.isActive()).map(plan -> new PlanView(
                plan.getCode().name(), plan.getName(), entitlements.findByPlanId(plan.getId()).stream()
                .map(item -> new EntitlementView(item.getFeature() == null ? item.getUsageLimit().name() : item.getFeature().name(),
                        item.isEnabled(), item.getLimitValue(), item.isUnlimited())).toList())).toList();
    }
    public record PlanView(String code, String name, List<EntitlementView> entitlements) {}
    public record EntitlementView(String code, boolean enabled, Integer limit, boolean unlimited) {}
}
