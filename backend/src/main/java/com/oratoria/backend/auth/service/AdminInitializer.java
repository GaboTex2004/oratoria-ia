package com.oratoria.backend.auth.service;

import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.PlanInitializationService;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
public class AdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private final UserAccountRepository users;
    private final SubscriptionRepository subscriptions;
    private final PlanInitializationService plans;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;

    public AdminInitializer(UserAccountRepository users, SubscriptionRepository subscriptions,
                            PlanInitializationService plans, PasswordEncoder passwordEncoder,
                            @Value("${app.admin.email}") String email,
                            @Value("${app.admin.password}") String password,
                            @Value("${app.admin.first-name}") String firstName,
                            @Value("${app.admin.last-name}") String lastName) {
        this.users = users; this.subscriptions = subscriptions; this.plans = plans;
        this.passwordEncoder = passwordEncoder; this.email = email; this.password = password;
        this.firstName = firstName; this.lastName = lastName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.length() < 8) {
            throw new IllegalStateException("ADMIN_EMAIL y ADMIN_PASSWORD (mínimo 8 caracteres) deben configurarse juntos.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalized)) {
            log.info("El usuario administrador configurado ya existe; no se modifica.");
            return;
        }
        UserAccount admin = users.save(new UserAccount(firstName.trim(), lastName.trim(), normalized,
                passwordEncoder.encode(password), UserRole.ADMIN));
        subscriptions.save(new Subscription(admin, plans.freePlan()));
        log.info("Usuario administrador inicial creado correctamente.");
    }
}
