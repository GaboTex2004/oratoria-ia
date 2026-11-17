package com.oratoria.backend.admin;

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
abstract class AdminTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PlanInitializationService plans;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    UserAccount user(UserRole role) {
        UserAccount user = users.saveAndFlush(new UserAccount("Admin", "Test", UUID.randomUUID() + "@example.test",
                encoder.encode("TestPassword123!"), role));
        subscriptions.save(new Subscription(user, plans.freePlan()));
        return user;
    }

    String auth(UserRole role) { return "Bearer " + jwt.createToken(user(role)); }
}
