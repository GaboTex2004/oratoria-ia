package com.oratoria.backend.admin.plan;

import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.shared.exception.ResourceNotFoundException;
import com.oratoria.backend.subscription.entity.*;
import com.oratoria.backend.subscription.repository.PlanEntitlementRepository;
import com.oratoria.backend.subscription.repository.PlanRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanAdminService {
    private final PlanRepository plans;
    private final PlanEntitlementRepository entitlements;

    public PlanAdminService(PlanRepository plans, PlanEntitlementRepository entitlements) {
        this.plans = plans; this.entitlements = entitlements;
    }

    @Transactional(readOnly = true)
    public List<PlanAdminView> list() {
        return plans.findAll().stream().sorted(Comparator.comparing(Plan::getCode)).map(plan -> new PlanAdminView(
                plan.getCode().name(), plan.getName(), plan.getPrice(), plan.getCurrency(), plan.isActive(),
                entitlements.findByPlanId(plan.getId()).stream().map(PlanAdminService::row)
                        .sorted(Comparator.comparing(EntitlementRow::kind).thenComparing(EntitlementRow::code)).toList())).toList();
    }

    @Transactional
    public void update(String code, PlanAdminController.PlanUpdate update) {
        PlanCode planCode = parse(PlanCode.class, code, "Plan inexistente.");
        Plan plan = plans.findByCode(planCode).orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado."));
        // FREE and PREMIUM are both required by registration, billing renewals and admin plan changes.
        if (!update.active()) throw new BadRequestException("Los planes FREE y PREMIUM no pueden desactivarse.");
        BigDecimal price = update.price().setScale(2, java.math.RoundingMode.HALF_UP);
        if (planCode == PlanCode.FREE && price.signum() != 0) throw new BadRequestException("El plan gratuito debe tener precio 0.");
        try {
            plan.configurePrice(price);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Precio de plan inválido.");
        }
        plan.rename(update.name().trim());
        plan.setActive(update.active());
        for (PlanAdminController.EntitlementUpdate item : update.entitlements()) apply(plan, item);
    }

    private void apply(Plan plan, PlanAdminController.EntitlementUpdate item) {
        Optional<Feature> feature = optional(Feature.class, item.code());
        if (feature.isPresent()) {
            entitlements.findByPlanIdAndFeature(plan.getId(), feature.get())
                    .orElseThrow(() -> new BadRequestException("Función no configurada para el plan: " + item.code()))
                    .setEnabled(item.enabled());
            return;
        }
        UsageLimit usageLimit = parse(UsageLimit.class, item.code(), "Función o límite desconocido: " + item.code());
        if (!item.unlimited() && (item.limit() == null || item.limit() < 0)) {
            throw new BadRequestException("El límite debe ser un número mayor o igual a 0.");
        }
        PlanEntitlement entitlement = entitlements.findByPlanIdAndUsageLimit(plan.getId(), usageLimit)
                .orElseThrow(() -> new BadRequestException("Límite no configurado para el plan: " + item.code()));
        entitlement.setEnabled(item.enabled());
        entitlement.configureLimit(item.limit(), item.unlimited());
    }

    private static EntitlementRow row(PlanEntitlement item) {
        boolean isFeature = item.getFeature() != null;
        return new EntitlementRow(isFeature ? item.getFeature().name() : item.getUsageLimit().name(),
                isFeature ? "FEATURE" : "LIMIT", item.isEnabled(), item.getLimitValue(), item.isUnlimited());
    }

    private static <E extends Enum<E>> Optional<E> optional(Class<E> type, String value) {
        try { return Optional.of(Enum.valueOf(type, value)); } catch (IllegalArgumentException | NullPointerException e) { return Optional.empty(); }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String message) {
        return optional(type, value).orElseThrow(() -> new BadRequestException(message));
    }

    public record PlanAdminView(String code, String name, BigDecimal price, String currency, boolean active, List<EntitlementRow> entitlements) {}
    public record EntitlementRow(String code, String kind, boolean enabled, Integer limit, boolean unlimited) {}
}
