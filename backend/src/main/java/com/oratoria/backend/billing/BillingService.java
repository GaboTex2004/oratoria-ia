package com.oratoria.backend.billing;

import com.oratoria.backend.billing.BillingStore.*;
import com.oratoria.backend.shared.exception.*;
import com.oratoria.backend.subscription.entity.*;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.PlanInitializationService;
import com.oratoria.backend.user.entity.*;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BillingService {
    public static final ZoneId ZONE = ZoneId.of("America/La_Paz");
    private final BillingStore store;
    private final SubscriptionRepository subscriptions;
    private final UserAccountRepository users;
    private final PlanInitializationService plans;
    private final DemoPaymentGuard guard;
    private final PaymentProvider provider;
    private final int graceDays;
    public BillingService(BillingStore store, SubscriptionRepository subscriptions, UserAccountRepository users,
            PlanInitializationService plans, DemoPaymentGuard guard, PaymentProvider provider,
            @Value("${app.billing.grace-days:3}") int graceDays) {
        this.store=store; this.subscriptions=subscriptions; this.users=users; this.plans=plans;
        this.guard=guard; this.provider=provider; this.graceDays=graceDays;
        if (graceDays < 1 || graceDays > 14) throw new IllegalArgumentException("Gracia inválida.");
    }
    private UserAccount lock(UUID user) { return users.lockForQuota(user).orElseThrow(()->new ResourceNotFoundException("Usuario no encontrado.")); }
    public UserAccount user(String email) { return users.findByEmailIgnoreCase(email).orElseThrow(()->new ResourceNotFoundException("Usuario no encontrado.")); }
    public Instant now(UUID user) { return guard.enabled() ? store.now(user) : Instant.now(); }
    private Instant monthAfter(Instant start) { return start.atZone(ZONE).plusMonths(1).toInstant(); }
    public Subscription effectiveSubscription(UUID user) {
        var account=lock(user);
        var active=subscriptions.findFirstByUserIdAndStatusOrderByStartsAtDesc(user, SubscriptionStatus.ACTIVE).orElse(null);
        Instant time=now(user);
        for (int period=0; active != null && active.isDemoManaged() && active.getEndsAt()!=null && !time.isBefore(active.getEndsAt()) && period<24; period++) {
            if (!active.isAutoRenew() || !guard.enabled()) {
                active.expire(!active.isAutoRenew());
                store.event(user,user,null,"SUBSCRIPTION_EXPIRED",time); active=null; break;
            }
            if (active.getGraceUntil()!=null) {
                if (!time.isBefore(active.getGraceUntil())) {
                    active.expire(false); store.event(user,user,null,"GRACE_EXPIRED",time); active=null;
                }
                break;
            }
            Instant start=active.getEndsAt();
            var order=new PaymentOrder(UUID.randomUUID(),user,active.getId(),plans.premiumPlan().getPrice(),"BOB","PENDING","RENEWAL",time,start,monthAfter(start));
            subscriptions.flush();
            store.create(order,"renewal:"+active.getId()+":"+start);
            store.event(user,user,order.id(),"RENEWAL_CREATED",time);
            var result=provider.process(new PaymentProvider.Order(order.id(),order.amount(),order.currency(),order.status()),PaymentProvider.Scenario.valueOf(active.getRenewalScenario()));
            store.record(order,PaymentProvider.Scenario.valueOf(active.getRenewalScenario()),result,time);
            store.event(user,user,order.id(),"RENEWAL_"+result.status(),time);
            if (result.status().equals("APPROVED")) active.renew(order.periodEnd());
            else active.grace(start.atZone(ZONE).plusDays(graceDays).toInstant());
        }
        if (active != null && active.isDemoManaged() && active.getEndsAt()!=null && !time.isBefore(active.getEndsAt())
                && (active.getGraceUntil()==null || !time.isBefore(active.getGraceUntil()))) {
            active.expire(false); store.event(user,user,null,"RECONCILIATION_LIMIT_EXPIRED",time); active=null;
        }
        if (active == null) {
            subscriptions.flush();
            active=subscriptions.saveAndFlush(new Subscription(account,plans.freePlan()));
        }
        return active;
    }
    public record SubscriptionView(UUID id, String plan, String status, Instant startsAt, Instant endsAt,
            Instant graceUntil, boolean autoRenew, boolean demoManaged, Instant effectiveNow,
            UUID pendingSubscriptionId, String pendingStatus, boolean simulatorEnabled, String timezone) {}
    public SubscriptionView current(UUID user) {
        var active=effectiveSubscription(user);
        var pending=subscriptions.findByUserIdOrderByStartsAtDesc(user).stream()
                .filter(sub->sub.getStatus()==SubscriptionStatus.PENDING_PAYMENT).findFirst().orElse(null);
        return new SubscriptionView(active.getId(),active.getPlan().getCode().name(),
                active.getGraceUntil()!=null ? "GRACE" : active.getStatus().name(),active.getStartsAt(),active.getEndsAt(),
                active.getGraceUntil(),active.isAutoRenew(),active.isDemoManaged(),now(user),
                pending==null?null:pending.getId(),pending==null?null:pending.getStatus().name(),guard.enabled(),ZONE.toString());
    }
    public record Receipt(boolean demonstration, PaymentOrder order, PaymentTransaction transaction,
            List<Map<String,Object>> events, String notice) {}
    private Receipt receipt(PaymentOrder order) {
        return new Receipt(true,order,store.transactionFor(order.id()).orElse(null),store.events(order.id()),
                "MODO DEMOSTRACIÓN — No se realizan cargos reales.");
    }
    private PaymentOrder owned(UUID user, UUID id) {
        return store.order(id).filter(order->order.userId().equals(user))
                .orElseThrow(()->new ResourceNotFoundException("Orden no encontrada."));
    }
    public Receipt order(UUID user, UUID id) { return receipt(owned(user,id)); }
    public Receipt transaction(UUID user, UUID id) {
        var transaction=store.transaction(id).orElseThrow(()->new ResourceNotFoundException("Transacción no encontrada."));
        return order(user,transaction.orderId());
    }
    public List<Receipt> history(UUID user,int page) { return store.history(user,page(page)).stream().map(this::receipt).toList(); }
    private int page(int page) { if (page<0 || page>10000) throw new BadRequestException("Página inválida."); return page; }
    public List<Receipt> global(String actor,int page) { admin(actor); return store.global(page(page)).stream().map(this::receipt).toList(); }
    public Receipt checkout(UUID user, String key) {
        guard.requireEnabled(); lock(user);
        if (key==null || !key.matches("[A-Za-z0-9_-]{8,100}")) throw new BadRequestException("Idempotency-Key debe contener de 8 a 100 caracteres seguros.");
        var existing=store.byKey(user,key); if (existing.isPresent()) return receipt(existing.get());
        var active=effectiveSubscription(user);
        if (active.getPlan().getCode()==PlanCode.PREMIUM) throw new BadRequestException("Ya tienes Premium; gestiona tu renovación desde Mi suscripción.");
        if (subscriptions.findByUserIdOrderByStartsAtDesc(user).stream().anyMatch(sub->sub.getStatus()==SubscriptionStatus.PENDING_PAYMENT))
            throw new BadRequestException("Ya existe un checkout pendiente. Continúa desde tu historial.");
        Instant time=now(user);
        var pending=new Subscription(active.getUser(),plans.premiumPlan()); pending.pending(time);
        subscriptions.saveAndFlush(pending);
        var order=new PaymentOrder(UUID.randomUUID(),user,pending.getId(),pending.getPlan().getPrice(),pending.getPlan().getCurrency(),"PENDING","CHECKOUT",time,null,null);
        store.create(order,key); store.event(user,user,order.id(),"CHECKOUT_CREATED",time);
        return receipt(order);
    }
    public Receipt process(UUID user,UUID id,PaymentProvider.Scenario scenario) {
        guard.requireEnabled(); lock(user); var order=owned(user,id);
        var existing=store.transactionFor(id);
        if (existing.isPresent()) {
            if (!existing.get().scenario().equals(scenario.name())) throw new BadRequestException("La orden ya se procesó con otro escenario.");
            return receipt(order);
        }
        if (!order.kind().equals("CHECKOUT")) throw new BadRequestException("Las renovaciones se procesan exclusivamente en el backend.");
        var subscription=subscriptions.findById(order.subscriptionId()).orElseThrow();
        if (subscription.getStatus()!=SubscriptionStatus.PENDING_PAYMENT) throw new BadRequestException("La suscripción no espera pago.");
        var result=provider.process(new PaymentProvider.Order(id,order.amount(),order.currency(),order.status()),scenario);
        Instant time=now(user);
        store.record(order,scenario,result,time);
        applyResult(order,subscription,result.status(),time);
        store.event(user,user,id,"PAYMENT_"+result.status(),time);
        return receipt(store.order(id).orElseThrow());
    }
    private void applyResult(PaymentOrder order,Subscription subscription,String status,Instant time) {
        if (status.equals("APPROVED")) {
            if (order.kind().equals("RENEWAL")) {
                if (subscription.getStatus()!=SubscriptionStatus.ACTIVE || subscription.getGraceUntil()==null || !time.isBefore(subscription.getGraceUntil()))
                    throw new BadRequestException("La renovación ya no admite confirmación.");
                subscription.renew(order.periodEnd());
            } else {
                subscriptions.findFirstByUserIdAndStatusOrderByStartsAtDesc(order.userId(),SubscriptionStatus.ACTIVE)
                        .ifPresent(previous->previous.expire(false));
                subscription.activate(time,monthAfter(time));
            }
        } else if (status.equals("REJECTED") && order.kind().equals("CHECKOUT")) subscription.reject();
        subscriptions.flush();
    }
    private UserAccount admin(String email) {
        var actor=user(email);
        if (actor.getRole()!=UserRole.ADMIN) throw new AccessDeniedException("Solo un administrador puede operar el simulador de pruebas.");
        return actor;
    }
    public Receipt confirm(String email,UUID orderId,boolean approved) {
        guard.requireEnabled(); var actor=admin(email);
        var original=store.order(orderId).orElseThrow(()->new ResourceNotFoundException("Orden no encontrada."));
        lock(original.userId()); var order=store.order(orderId).orElseThrow();
        var transaction=store.transactionFor(orderId).orElseThrow(()->new BadRequestException("Primero procesa el escenario pendiente."));
        if (!order.status().equals("PENDING") || !transaction.status().equals("PENDING")) throw new BadRequestException("La transacción ya no está pendiente.");
        var subscription=subscriptions.findById(order.subscriptionId()).orElseThrow();
        if (order.kind().equals("CHECKOUT") && subscription.getStatus()!=SubscriptionStatus.PENDING_PAYMENT)
            throw new BadRequestException("La suscripción ya no está pendiente.");
        Instant time=now(order.userId()); String status=approved?"APPROVED":"REJECTED";
        applyResult(order,subscription,status,time);
        store.resolve(order,status,approved?"DEMO-"+UUID.randomUUID().toString().substring(0,12):null,time);
        store.event(order.userId(),actor.getId(),orderId,"ADMIN_CONFIRM_"+status,time);
        return receipt(store.order(orderId).orElseThrow());
    }
    public SubscriptionView cancel(UUID user) {
        guard.requireEnabled(); var subscription=effectiveSubscription(user);
        if (subscription.getPlan().getCode()!=PlanCode.PREMIUM || !subscription.isDemoManaged())
            throw new BadRequestException("No hay una suscripción de demostración que cancelar.");
        if (subscription.isAutoRenew()) {
            subscription.cancelRenewal(); store.event(user,user,null,"RENEWAL_CANCELED",now(user));
        }
        return current(user);
    }
    public SubscriptionView advance(String email,UUID user,int days,PaymentProvider.Scenario scenario) {
        guard.requireTimeTravel(); var actor=admin(email); lock(user);
        if (days<1 || days>400) throw new BadRequestException("El avance debe ser de 1 a 400 días.");
        var subscription=effectiveSubscription(user);
        subscription.renewalScenario(scenario.name()); subscriptions.flush();
        Instant time=store.advance(user,days); store.event(user,actor.getId(),null,"CLOCK_ADVANCED_"+days,time);
        return current(user);
    }
}
