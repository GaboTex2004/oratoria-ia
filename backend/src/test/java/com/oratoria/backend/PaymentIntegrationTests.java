package com.oratoria.backend;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.oratoria.backend.billing.*;
import com.oratoria.backend.billing.PaymentProvider.Scenario;
import com.oratoria.backend.auth.security.JwtService;
import com.oratoria.backend.subscription.entity.*;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.*;
import com.oratoria.backend.user.entity.*;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

@SpringBootTest(properties="app.billing.demo-enabled=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class PaymentIntegrationTests {
    @Autowired BillingService billing;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired UserAccountRepository users;
    @Autowired PlanInitializationService plans;
    @Autowired FeatureAccessService access;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentProvider provider;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    UserAccount user(UserRole role) {
        var user=users.saveAndFlush(new UserAccount("Pago", "Sintético", UUID.randomUUID()+"@example.test", "unused-hash", role));
        subscriptions.saveAndFlush(new Subscription(user,plans.freePlan())); return user;
    }
    BillingService.Receipt checkout(UserAccount user) { return billing.checkout(user.getId(),UUID.randomUUID().toString()); }
    String auth(UserAccount user) { return "Bearer "+jwt.createToken(user); }
    @Test void freeHasFiniteUsageAndConfiguredPrice() {
        var user=user(UserRole.USER);
        assertThat(billing.current(user.getId()).plan()).isEqualTo("FREE");
        assertThat(access.limitFor(user.getId(),UsageLimit.SESSIONS_PER_MONTH).orElseThrow().value()).isEqualTo(5);
        assertThat(plans.premiumPlan().getPrice()).isEqualByComparingTo("49.00");
    }
    @Test void createsPendingSubscriptionButKeepsFreeAndSnapshotsPrice() {
        var user=user(UserRole.USER); var order=checkout(user);
        assertThat(order.order().amount()).isEqualByComparingTo(plans.premiumPlan().getPrice());
        assertThat(order.order().currency()).isEqualTo("BOB");
        assertThat(billing.current(user.getId()).pendingStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(billing.current(user.getId()).plan()).isEqualTo("FREE");
    }
    @Test void approvedActivatesPremiumAndAuditsExactlyOnce() {
        var user=user(UserRole.USER); var order=checkout(user);
        var result=billing.process(user.getId(),order.order().id(),Scenario.DEMO_APPROVED);
        assertThat(result.transaction().status()).isEqualTo("APPROVED");
        assertThat(result.transaction().authorizationCode()).startsWith("DEMO-");
        var current=billing.current(user.getId());
        assertThat(current.plan()).isEqualTo("PREMIUM"); assertThat(current.autoRenew()).isTrue();
        assertThat(current.endsAt()).isEqualTo(current.startsAt().atZone(BillingService.ZONE).plusMonths(1).toInstant());
        assertThat(access.hasAccess(user.getId(),Feature.CAMERA_ANALYSIS)).isTrue();
        assertThat(access.limitFor(user.getId(),UsageLimit.SESSIONS_PER_MONTH).orElseThrow().unlimited()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_audit_logs WHERE user_id=? AND action='PAYMENT_APPROVED'",Long.class,user.getId())).isEqualTo(1);
    }
    @Test void declineNeverActivatesAndAllowsNewCheckout() {
        var user=user(UserRole.USER); var order=checkout(user);
        assertThat(billing.process(user.getId(),order.order().id(),Scenario.DEMO_DECLINED).transaction().status()).isEqualTo("REJECTED");
        assertThat(billing.current(user.getId()).plan()).isEqualTo("FREE");
        assertThat(checkout(user).order().id()).isNotEqualTo(order.order().id());
    }
    @Test void pendingWaitsForAdministratorConfirmation() {
        var user=user(UserRole.USER); var admin=user(UserRole.ADMIN); var order=checkout(user);
        billing.process(user.getId(),order.order().id(),Scenario.DEMO_PENDING);
        assertThat(billing.current(user.getId()).plan()).isEqualTo("FREE");
        billing.confirm(admin.getEmail(),order.order().id(),true);
        assertThat(billing.current(user.getId()).plan()).isEqualTo("PREMIUM");
    }
    @Test void administratorCanRejectPending() {
        var user=user(UserRole.USER); var admin=user(UserRole.ADMIN); var order=checkout(user);
        billing.process(user.getId(),order.order().id(),Scenario.DEMO_PENDING);
        billing.confirm(admin.getEmail(),order.order().id(),false);
        assertThat(billing.current(user.getId()).plan()).isEqualTo("FREE");
        assertThat(billing.order(user.getId(),order.order().id()).transaction().status()).isEqualTo("REJECTED");
    }
    @Test void checkoutKeyAndDoubleSubmissionReturnSameObjects() {
        var user=user(UserRole.USER); String key=UUID.randomUUID().toString();
        var order=billing.checkout(user.getId(),key);
        assertThat(billing.checkout(user.getId(),key).order().id()).isEqualTo(order.order().id());
        var first=billing.process(user.getId(),order.order().id(),Scenario.DEMO_APPROVED);
        var end=billing.current(user.getId()).endsAt();
        var again=billing.process(user.getId(),order.order().id(),Scenario.DEMO_APPROVED);
        assertThat(again.transaction().id()).isEqualTo(first.transaction().id());
        assertThat(billing.current(user.getId()).endsAt()).isEqualTo(end);
        assertThat(subscriptions.countByUserId(user.getId())).isEqualTo(2);
    }
    @Test void cannotActivatePendingTwice() {
        var user=user(UserRole.USER); var admin=user(UserRole.ADMIN); var order=checkout(user);
        billing.process(user.getId(),order.order().id(),Scenario.DEMO_PENDING);
        billing.confirm(admin.getEmail(),order.order().id(),true);
        assertThatThrownBy(()->billing.confirm(admin.getEmail(),order.order().id(),true)).isInstanceOf(com.oratoria.backend.shared.exception.BadRequestException.class);
    }
    @Test void cancelKeepsPaidPeriodThenDowngradesWithoutDeletingTraining() throws Exception {
        var user=user(UserRole.USER); var admin=user(UserRole.ADMIN); var order=checkout(user);
        billing.process(user.getId(),order.order().id(),Scenario.DEMO_APPROVED);
        mvc.perform(post("/api/sessions").header("Authorization",auth(user)).contentType(MediaType.APPLICATION_JSON).content("{\"trainingType\":\"PRESENTATION\"}")).andExpect(status().isCreated());
        var end=billing.current(user.getId()).endsAt();
        assertThat(billing.cancel(user.getId()).plan()).isEqualTo("PREMIUM");
        assertThat(billing.current(user.getId()).endsAt()).isEqualTo(end);
        billing.advance(admin.getEmail(),user.getId(),40,Scenario.DEMO_APPROVED);
        assertThat(billing.current(user.getId()).plan()).isEqualTo("FREE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM training_sessions WHERE user_id=?",Long.class,user.getId())).isEqualTo(1);
        assertThat(access.hasAccess(user.getId(),Feature.CAMERA_ANALYSIS)).isFalse();
    }
    @Test void approvedRenewalExtendsOneCalendarMonth() {
        var user=user(UserRole.USER); var admin=user(UserRole.ADMIN); var order=checkout(user);
        billing.process(user.getId(),order.order().id(),Scenario.DEMO_APPROVED);
        var end=billing.current(user.getId()).endsAt();
        billing.advance(admin.getEmail(),user.getId(),35,Scenario.DEMO_APPROVED);
        assertThat(billing.current(user.getId()).endsAt()).isEqualTo(end.atZone(BillingService.ZONE).plusMonths(1).toInstant());
        assertThat(billing.history(user.getId(),0)).hasSize(2);
    }
    @Test void rejectedRenewalEntersGraceThenExpires() {
        var user=user(UserRole.USER); var admin=user(UserRole.ADMIN); var order=checkout(user);
        billing.process(user.getId(),order.order().id(),Scenario.DEMO_APPROVED);
        var current=billing.current(user.getId());
        int days=(int)java.time.temporal.ChronoUnit.DAYS.between(current.effectiveNow(),current.endsAt())+2;
        assertThat(billing.advance(admin.getEmail(),user.getId(),days,Scenario.DEMO_DECLINED).status()).isEqualTo("GRACE");
        assertThat(billing.advance(admin.getEmail(),user.getId(),4,Scenario.DEMO_DECLINED).plan()).isEqualTo("FREE");
    }
    @Test void pendingRenewalCanBeConfirmedWithinGrace() {
        var user=user(UserRole.USER); var admin=user(UserRole.ADMIN); var order=checkout(user);
        billing.process(user.getId(),order.order().id(),Scenario.DEMO_APPROVED);
        var current=billing.current(user.getId());
        int days=(int)java.time.temporal.ChronoUnit.DAYS.between(current.effectiveNow(),current.endsAt())+2;
        billing.advance(admin.getEmail(),user.getId(),days,Scenario.DEMO_PENDING);
        var renewal=billing.history(user.getId(),0).stream().filter(item->item.order().kind().equals("RENEWAL")).findFirst().orElseThrow();
        billing.confirm(admin.getEmail(),renewal.order().id(),true);
        assertThat(billing.current(user.getId()).status()).isEqualTo("ACTIVE");
    }
    @Test void invalidMoneyCurrencyOrSettledOrderIsRejectedByProvider() {
        assertThatThrownBy(()->provider.process(new PaymentProvider.Order(UUID.randomUUID(),BigDecimal.ZERO,"BOB","PENDING"),Scenario.DEMO_APPROVED)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->provider.process(new PaymentProvider.Order(UUID.randomUUID(),new BigDecimal("49.00"),"USD","PENDING"),Scenario.DEMO_APPROVED)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->provider.process(new PaymentProvider.Order(UUID.randomUUID(),new BigDecimal("49.00"),"BOB","APPROVED"),Scenario.DEMO_APPROVED)).isInstanceOf(RuntimeException.class);
    }
    @Test void strangersCannotReadOrProcessOthersOrdersOrTransactions() throws Exception {
        var owner=user(UserRole.USER); var stranger=user(UserRole.USER); var order=checkout(owner);
        var result=billing.process(owner.getId(),order.order().id(),Scenario.DEMO_APPROVED);
        mvc.perform(get("/api/payments/orders/"+order.order().id()).header("Authorization",auth(stranger))).andExpect(status().isNotFound());
        mvc.perform(get("/api/payments/transactions/"+result.transaction().id()).header("Authorization",auth(stranger))).andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/orders/"+order.order().id()+"/process").header("Authorization",auth(stranger)).contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":\"DEMO_APPROVED\"}")).andExpect(status().isNotFound());
    }
    @Test void unauthenticatedAndNonAdminRequestsAreRejected() throws Exception {
        var user=user(UserRole.USER);
        mvc.perform(get("/api/payments/history")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/payments/users/"+user.getId()+"/advance").header("Authorization",auth(user)).contentType(MediaType.APPLICATION_JSON).content("{\"days\":35,\"renewalScenario\":\"DEMO_APPROVED\"}")).andExpect(status().isForbidden());
        assertThatThrownBy(()->billing.advance(user.getEmail(),user.getId(),35,Scenario.DEMO_APPROVED)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void forgedStatusCannotApproveWithoutScenarioAndConsumptionIsReal() throws Exception {
        var user=user(UserRole.USER); var order=checkout(user);
        mvc.perform(post("/api/payments/orders/"+order.order().id()+"/process").header("Authorization",auth(user)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}")).andExpect(status().isBadRequest());
        assertThat(billing.current(user.getId()).plan()).isEqualTo("FREE");
        mvc.perform(get("/api/subscriptions/current/usage").header("Authorization",auth(user))).andExpect(status().isOk()).andExpect(jsonPath("$.practices.used").value(0));
    }
    @Test void missingIdempotencyHeaderIsBadRequest() throws Exception {
        var user=user(UserRole.USER);
        mvc.perform(post("/api/payments/orders").header("Authorization",auth(user))).andExpect(status().isBadRequest());
    }
    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousSubmissionsSerializeAndActivateOnlyOnce() throws Exception {
        var template=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var user=template.execute(status->user(UserRole.USER));
        var order=checkout(user);
        try (var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<BillingService.Receipt> action=()->{
                gate.await(); return billing.process(user.getId(),order.order().id(),Scenario.DEMO_APPROVED);
            };
            var first=executor.submit(action); var second=executor.submit(action); gate.countDown();
            var a=first.get(15,java.util.concurrent.TimeUnit.SECONDS);
            var b=second.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(a.transaction().id()).isEqualTo(b.transaction().id());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transactions WHERE order_id=?",Long.class,order.order().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE user_id=? AND status='ACTIVE'",Long.class,user.getId())).isEqualTo(1);
    }
}
