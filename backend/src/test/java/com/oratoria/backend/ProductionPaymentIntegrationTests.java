package com.oratoria.backend;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.oratoria.backend.billing.*;
import com.oratoria.backend.auth.security.JwtService;
import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.PlanInitializationService;
import com.oratoria.backend.user.entity.*;
import com.oratoria.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

@SpringBootTest(properties="app.billing.demo-enabled=true")
@ActiveProfiles({"production","dev"})
@AutoConfigureMockMvc
@Transactional
class ProductionPaymentIntegrationTests {
    @Autowired DemoPaymentGuard guard;
    @Autowired UserAccountRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PlanInitializationService plans;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Test void productionWinsEvenIfDevAndEnabledFlagArePresent() throws Exception {
        assertThat(guard.enabled()).isFalse();
        var user=users.saveAndFlush(new UserAccount("Prod","Test",UUID.randomUUID()+"@example.test","unused",UserRole.USER));
        subscriptions.saveAndFlush(new Subscription(user,plans.freePlan()));
        mvc.perform(post("/api/payments/orders").header("Authorization","Bearer "+jwt.createToken(user)).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isForbidden());
    }
    @Test void profileAndExplicitSwitchAreBothRequired() {
        var environment=new org.springframework.mock.env.MockEnvironment();
        assertThat(new DemoPaymentGuard(environment,true).enabled()).isFalse();
        for (String profile:java.util.List.of("dev","test","demo")) {
            environment.setActiveProfiles(profile);
            assertThat(new DemoPaymentGuard(environment,true).enabled()).isTrue();
            assertThat(new DemoPaymentGuard(environment,false).enabled()).isFalse();
        }
        environment.setActiveProfiles("dev","prod");
        assertThat(new DemoPaymentGuard(environment,true).enabled()).isFalse();
    }
}
