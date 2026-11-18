package com.oratoria.backend.sprint4;

import com.oratoria.backend.auth.security.JwtService;
import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.PlanInitializationService;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class Sprint4TestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PlanInitializationService plans;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    UserAccount free() { return user(false); }
    UserAccount premium() { return user(true); }

    UserAccount user(boolean premium) {
        UserAccount user = users.saveAndFlush(new UserAccount("Ana", "Prueba", UUID.randomUUID() + "@example.test",
                encoder.encode("TestPassword123!"), UserRole.USER));
        subscriptions.saveAndFlush(new Subscription(user, premium ? plans.premiumPlan() : plans.freePlan()));
        return user;
    }

    String auth(UserAccount user) { return "Bearer " + jwt.createToken(user); }
}
