package com.oratoria.backend.auth.service;

import com.oratoria.backend.auth.dto.AuthResponse;
import com.oratoria.backend.auth.dto.LoginRequest;
import com.oratoria.backend.auth.dto.RegisterRequest;
import com.oratoria.backend.auth.exception.DuplicateEmailException;
import com.oratoria.backend.auth.exception.InvalidCredentialsException;
import com.oratoria.backend.auth.security.JwtService;
import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.entity.SubscriptionStatus;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.PlanInitializationService;
import com.oratoria.backend.user.dto.UserResponse;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final SubscriptionRepository subscriptions;
    private final PlanInitializationService plans;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserAccountRepository users, SubscriptionRepository subscriptions,
                       PlanInitializationService plans, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.users = users;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) throw new DuplicateEmailException();
        try {
            UserAccount user = users.save(new UserAccount(
                    request.firstName().trim(), request.lastName().trim(), email,
                    passwordEncoder.encode(request.password()), UserRole.USER));
            Subscription subscription = subscriptions.save(new Subscription(user, plans.freePlan()));
            return new AuthResponse(jwtService.createToken(user), toResponse(user, subscription));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateEmailException();
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserAccount user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(UserAccount::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return new AuthResponse(jwtService.createToken(user), currentUser(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String email) {
        UserAccount user = users.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);
        return currentUser(user);
    }

    private UserResponse currentUser(UserAccount user) {
        Subscription subscription = subscriptions.findFirstByUserIdAndStatusOrderByStartsAtDesc(
                user.getId(), SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("El usuario no tiene una suscripción activa."));
        return toResponse(user, subscription);
    }

    private UserResponse toResponse(UserAccount user, Subscription subscription) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getRole(), subscription.getPlan().getCode());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
