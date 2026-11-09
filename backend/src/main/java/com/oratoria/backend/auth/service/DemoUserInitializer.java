package com.oratoria.backend.auth.service;

import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.service.PlanInitializationService;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import com.oratoria.backend.user.repository.UserAccountRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(25)
public class DemoUserInitializer implements ApplicationRunner {
    private final UserAccountRepository users;
    private final SubscriptionRepository subscriptions;
    private final PlanInitializationService plans;
    private final PasswordEncoder encoder;
    private final Environment env;

    public DemoUserInitializer(UserAccountRepository users, SubscriptionRepository subscriptions,
            PlanInitializationService plans, PasswordEncoder encoder, Environment env) {
        this.users = users; this.subscriptions = subscriptions; this.plans = plans;
        this.encoder = encoder; this.env = env;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String tier : new String[]{"FREE", "PREMIUM"}) {
            String email = env.getProperty("DEMO_" + tier + "_EMAIL", "").trim().toLowerCase(java.util.Locale.ROOT);
            String password = env.getProperty("DEMO_" + tier + "_PASSWORD", "");
            if (email.isBlank() && password.isBlank()) continue;
            if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || password.length() < 12
                    || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                throw new IllegalStateException("Configura email y contraseña demo de 12 a 72 bytes para " + tier);
            }
            if (users.existsByEmailIgnoreCase(email)) continue;
            UserAccount user = users.save(new UserAccount("Demo", tier, email, encoder.encode(password), UserRole.USER));
            subscriptions.save(new Subscription(user, tier.equals("FREE") ? plans.freePlan() : plans.premiumPlan()));
        }
    }
}
