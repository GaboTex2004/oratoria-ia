package com.oratoria.backend.billing;

import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import com.oratoria.backend.billing.PaymentProvider.Scenario;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.simulation.repository.SimulationSessionRepository;
import com.oratoria.backend.subscription.entity.UsageLimit;
import com.oratoria.backend.subscription.service.*;

@RestController
@RequestMapping("/api")
public class BillingController {
    private final BillingService billing;
    private final FeatureAccessService access;
    private final TrainingSessionRepository sessions;
    private final SimulationSessionRepository simulations;
    public BillingController(BillingService billing,FeatureAccessService access,
            TrainingSessionRepository sessions,SimulationSessionRepository simulations) {
        this.billing=billing; this.access=access; this.sessions=sessions; this.simulations=simulations;
    }
    private UUID user(Principal actor) { return billing.user(actor.getName()).getId(); }
    public record ProcessRequest(@NotNull Scenario scenario) {}
    public record ConfirmRequest(@NotNull Decision decision) {}
    public enum Decision { APPROVE, REJECT }
    public record AdvanceRequest(@Min(1) @Max(400) int days,@NotNull Scenario renewalScenario) {}
    @GetMapping("/subscriptions/current")
    public BillingService.SubscriptionView current(Principal actor) { return billing.current(user(actor)); }
    @PostMapping("/payments/orders")
    public BillingService.Receipt checkout(Principal actor,@RequestHeader("Idempotency-Key") String key) { return billing.checkout(user(actor),key); }
    @GetMapping("/payments/orders/{id}")
    public BillingService.Receipt order(Principal actor,@PathVariable UUID id) { return billing.order(user(actor),id); }
    @PostMapping("/payments/orders/{id}/process")
    public BillingService.Receipt process(Principal actor,@PathVariable UUID id,@Valid @RequestBody ProcessRequest body) { return billing.process(user(actor),id,body.scenario()); }
    @GetMapping("/payments/transactions/{id}")
    public BillingService.Receipt transaction(Principal actor,@PathVariable UUID id) { return billing.transaction(user(actor),id); }
    @GetMapping("/payments/history")
    public List<BillingService.Receipt> history(Principal actor,@RequestParam(defaultValue="0") int page) { return billing.history(user(actor),page); }
    @PostMapping("/subscriptions/current/cancel-renewal")
    public BillingService.SubscriptionView cancel(Principal actor) { return billing.cancel(user(actor)); }
    @GetMapping("/subscriptions/current/usage")
    @Transactional
    public Map<String,Object> usage(Principal actor) {
        UUID user=user(actor); var practice=access.limitFor(user,UsageLimit.SESSIONS_PER_MONTH).orElseThrow();
        var conversation=access.limitFor(user,UsageLimit.SIMULATIONS_PER_MONTH).orElseThrow();
        return Map.of("periodStart",QuotaService.monthStart(),"quotaTimezone","UTC","billingTimezone","America/La_Paz",
                "practices",Map.of("used",sessions.countByUserIdAndCreatedAtGreaterThanEqual(user,QuotaService.monthStart()),"unlimited",practice.unlimited(),"limit",practice.value()==null?0:practice.value()),
                "simulations",Map.of("used",simulations.countByUserIdAndCreatedAtGreaterThanEqual(user,QuotaService.monthStart()),"unlimited",conversation.unlimited(),"limit",conversation.value()==null?0:conversation.value()));
    }
    @GetMapping("/admin/payments")
    public List<BillingService.Receipt> global(Principal actor,@RequestParam(defaultValue="0") int page) { return billing.global(actor.getName(),page); }
    @PostMapping("/admin/payments/orders/{id}/confirm")
    public BillingService.Receipt confirm(Principal actor,@PathVariable UUID id,@Valid @RequestBody ConfirmRequest body) { return billing.confirm(actor.getName(),id,body.decision()==Decision.APPROVE); }
    @PostMapping("/admin/payments/users/{id}/advance")
    public BillingService.SubscriptionView advance(Principal actor,@PathVariable UUID id,@Valid @RequestBody AdvanceRequest body) { return billing.advance(actor.getName(),id,body.days(),body.renewalScenario()); }
}
