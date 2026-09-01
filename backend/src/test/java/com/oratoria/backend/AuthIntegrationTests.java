package com.oratoria.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oratoria.backend.auth.security.JwtService;
import com.oratoria.backend.subscription.entity.Subscription;
import com.oratoria.backend.subscription.repository.SubscriptionRepository;
import com.oratoria.backend.subscription.service.PlanInitializationService;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import com.oratoria.backend.user.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PlanInitializationService plans;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    @Test
    void registrationCreatesUserAndExactlyOneFreeSubscription() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("registration@example.test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.plan").value("FREE"));

        UserAccount user = users.findByEmailIgnoreCase("registration@example.test").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.getPasswordHash()).startsWith("$2");
        org.assertj.core.api.Assertions.assertThat(subscriptions.countByUserId(user.getId())).isEqualTo(1);
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("duplicate@example.test"))).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("DUPLICATE@example.test")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void loginAcceptsCorrectPasswordAndRejectsWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("login@example.test"))).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("login@example.test", "Correct123!")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("login@example.test", "wrong-password")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void protectedEndpointsEnforceAuthenticationAndAdminRole() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test")).andExpect(status().isOk());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("role-user@example.test"))).andExpect(status().isCreated());
        UserAccount regular = users.findByEmailIgnoreCase("role-user@example.test").orElseThrow();
        String userToken = jwtService.createToken(regular);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/admin/status").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        UserAccount admin = users.save(new UserAccount("Test", "Admin", "role-admin@example.test",
                passwordEncoder.encode("Admin123!"), UserRole.ADMIN));
        subscriptions.save(new Subscription(admin, plans.freePlan()));
        String adminToken = jwtService.createToken(admin);
        mvc.perform(get("/api/admin/status").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    private String registerJson(String email) {
        return """
                {"firstName":"Test","lastName":"User","email":"%s","password":"Correct123!"}
                """.formatted(email);
    }

    private String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }
}
