package com.oratoria.backend.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.oratoria.backend.auth.security.JwtService;
import com.oratoria.backend.notification.PushGateway.Delivery;
import com.oratoria.backend.notification.PushGateway.Target;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Sin @Transactional: el aviso de resultado se envía desde otro hilo y necesita ver los datos confirmados. */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTests {
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService notifications;
    @Autowired ApplicationEventPublisher events;
    @Autowired NotificationEvents queue;
    @MockitoBean PushGateway gateway;

    @BeforeEach void deliverByDefault() {
        reset(gateway);
        when(gateway.send(any(), any())).thenReturn(Delivery.DELIVERED);
    }

    UserAccount user() {
        return users.saveAndFlush(new UserAccount("Ana", "Push", UUID.randomUUID() + "@example.test",
                encoder.encode("TestPassword123!"), UserRole.USER));
    }

    String auth(UserAccount user) { return "Bearer " + jwt.createToken(user); }

    static String subscription(String endpoint) {
        String key = B64.encodeToString(WebPushCrypto.encodePoint((ECPublicKey) WebPushCrypto.newKeyPair().getPublic()));
        return "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + key + "\",\"auth\":\"" + B64.encodeToString(new byte[16]) + "\"}}";
    }

    static String fcm() { return "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID(); }

    void subscribe(UserAccount user, String endpoint) throws Exception {
        mvc.perform(post("/api/notifications/subscriptions").header("Authorization", auth(user))
                .contentType(MediaType.APPLICATION_JSON).content(subscription(endpoint))).andExpect(status().isNoContent());
    }

    void expectRegistered(UserAccount user, String endpoint, boolean expected) throws Exception {
        mvc.perform(post("/api/notifications/subscriptions/status").header("Authorization", auth(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registered").value(expected));
    }

    /** Sin @Transactional: cada prueba borra lo que creó para no dejar suscripciones a las siguientes corridas. */
    @AfterEach void cleanUp() {
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id IN (SELECT id FROM app_users WHERE email LIKE '%@example.test' AND last_name = 'Push')");
        jdbc.update("DELETE FROM notification_settings WHERE user_id IN (SELECT id FROM app_users WHERE email LIKE '%@example.test' AND last_name = 'Push')");
    }

    @Test void configExposesOnlyThePublicKeyAndRequiresLogin() throws Exception {
        mvc.perform(get("/api/notifications/config")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/config").header("Authorization", auth(user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").isString())
                .andExpect(jsonPath("$.privateKey").doesNotExist());
    }

    @Test void subscribingSendingATestAndUnsubscribing() throws Exception {
        UserAccount user = user();
        String endpoint = fcm();
        mvc.perform(post("/api/notifications/test").header("Authorization", auth(user))).andExpect(status().isBadRequest());
        expectRegistered(user, endpoint, false);
        subscribe(user, endpoint);
        subscribe(user, endpoint); // repetir no duplica
        expectRegistered(user, endpoint, true);
        // Otra cuenta en el mismo navegador no lo ve como propio.
        expectRegistered(user(), endpoint, false);
        mvc.perform(get("/api/notifications/settings").header("Authorization", auth(user))).andExpect(jsonPath("$.devices").value(1));

        mvc.perform(post("/api/notifications/test").header("Authorization", auth(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.delivered").value(1));
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(gateway).send(argThat(target -> target.endpoint().equals(endpoint)), payload.capture());
        assertThat(new String(payload.getValue(), StandardCharsets.UTF_8)).contains("\"title\":\"OratoriaAI\"").contains("\"url\":\"/home\"");

        mvc.perform(delete("/api/notifications/subscriptions").header("Authorization", auth(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/settings").header("Authorization", auth(user))).andExpect(jsonPath("$.devices").value(0));
    }

    @Test void endpointsOutsideKnownPushServicesAreRejected() throws Exception {
        UserAccount user = user();
        for (String endpoint : new String[]{"http://fcm.googleapis.com/x", "https://169.254.169.254/latest", "https://evil.example/fcm.googleapis.com",
                "https://fcm.googleapis.com.evil.example/x", "https://user@fcm.googleapis.com/x"}) {
            mvc.perform(post("/api/notifications/subscriptions").header("Authorization", auth(user))
                    .contentType(MediaType.APPLICATION_JSON).content(subscription(endpoint))).andExpect(status().isBadRequest());
        }
        subscribe(user, "https://updates.push.services.mozilla.com/wpush/v2/" + UUID.randomUUID());
        subscribe(user, "https://wns2-par02p.notify.windows.com/w/?token=" + UUID.randomUUID());
        subscribe(user, "https://web.push.apple.com/" + UUID.randomUUID());
    }

    @Test void subscriptionsTheBrowserRevokedAreRemoved() throws Exception {
        UserAccount user = user();
        subscribe(user, fcm());
        when(gateway.send(any(), any())).thenReturn(Delivery.GONE);
        mvc.perform(post("/api/notifications/test").header("Authorization", auth(user))).andExpect(jsonPath("$.removed").value(1));
        mvc.perform(get("/api/notifications/settings").header("Authorization", auth(user))).andExpect(jsonPath("$.devices").value(0));
    }

    @Test void aServerSideRejectionKeepsTheSubscription() throws Exception {
        UserAccount user = user();
        subscribe(user, fcm());
        // 401/403/413/429: problema del servidor (VAPID, reloj, cuota), no de la suscripción.
        when(gateway.send(any(), any())).thenReturn(Delivery.REJECTED);
        for (int attempt = 0; attempt < NotificationService.MAX_FAILURES + 1; attempt++) {
            mvc.perform(post("/api/notifications/test").header("Authorization", auth(user))).andExpect(jsonPath("$.failed").value(1));
        }
        mvc.perform(get("/api/notifications/settings").header("Authorization", auth(user))).andExpect(jsonPath("$.devices").value(1));
    }

    @Test void changingThePasswordUnlinksEveryDevice() throws Exception {
        UserAccount user = user();
        subscribe(user, fcm());
        mvc.perform(post("/api/users/me/password").header("Authorization", auth(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"TestPassword123!\",\"newPassword\":\"OtraClave-456!\"}")).andExpect(status().is2xxSuccessful());
        Integer devices = jdbc.queryForObject("SELECT count(*) FROM push_subscriptions WHERE user_id = ?", Integer.class, user.getId());
        assertThat(devices).isZero();
    }

    @Test void anotherAccountCannotTakeOverAnEndpointWithDifferentKeys() throws Exception {
        UserAccount owner = user();
        String endpoint = fcm();
        subscribe(owner, endpoint);
        mvc.perform(post("/api/notifications/subscriptions").header("Authorization", auth(user()))
                .contentType(MediaType.APPLICATION_JSON).content(subscription(endpoint))).andExpect(status().isBadRequest());
        expectRegistered(owner, endpoint, true);
    }

    @Test void settingsAreValidatedAndPersisted() throws Exception {
        UserAccount user = user();
        mvc.perform(get("/api/notifications/settings").header("Authorization", auth(user)))
                .andExpect(jsonPath("$.practiceReady").value(true)).andExpect(jsonPath("$.dailyReminder").value(false));
        mvc.perform(put("/api/notifications/settings").header("Authorization", auth(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"practiceReady\":false,\"dailyReminder\":true,\"reminderHour\":24,\"timeZone\":\"America/La_Paz\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/notifications/settings").header("Authorization", auth(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"practiceReady\":false,\"dailyReminder\":true,\"reminderHour\":8,\"timeZone\":\"Marte/Olympus\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/notifications/settings").header("Authorization", auth(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"practiceReady\":false,\"dailyReminder\":true,\"reminderHour\":8,\"timeZone\":\"America/Lima\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reminderHour").value(8)).andExpect(jsonPath("$.timeZone").value("America/Lima"));
    }

    UUID session(UserAccount user) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO training_sessions (id, created_at, status, training_type, user_id) VALUES (?, now(), 'COMPLETED', 'PRESENTATION', ?)",
                id, user.getId());
        return id;
    }

    @Test void finishingAnEvaluationNotifiesTheOwnerWithALinkToTheReport() throws Exception {
        UserAccount user = user();
        String endpoint = fcm();
        subscribe(user, endpoint);
        UUID sessionId = session(user);

        events.publishEvent(new PracticeEvaluatedEvent(sessionId));

        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(gateway, timeout(5000)).send(argThat(target -> target.endpoint().equals(endpoint)), payload.capture());
        assertThat(new String(payload.getValue(), StandardCharsets.UTF_8))
                .contains("Tu resultado está listo").contains("\"url\":\"/reports/" + sessionId + "\"");
    }

    @Test void theResultNoticeRespectsTheSetting() throws Exception {
        UserAccount user = user();
        String endpoint = fcm();
        subscribe(user, endpoint);
        mvc.perform(put("/api/notifications/settings").header("Authorization", auth(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"practiceReady\":false,\"dailyReminder\":false,\"reminderHour\":19,\"timeZone\":\"America/La_Paz\"}"));
        notifications.notifyPracticeReady(session(user));
        verify(gateway, never()).send(argThat(target -> target.endpoint().equals(endpoint)), any());
    }

    @Test void theDailyReminderIsSentOnceAfterTheChosenHourAndSkipsPeopleWhoAlreadyPracticed() throws Exception {
        UserAccount waiting = user();
        UserAccount practiced = user();
        String waitingEndpoint = fcm();
        String practicedEndpoint = fcm();
        subscribe(waiting, waitingEndpoint);
        subscribe(practiced, practicedEndpoint);
        for (UserAccount user : new UserAccount[]{waiting, practiced}) {
            mvc.perform(put("/api/notifications/settings").header("Authorization", auth(user)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"practiceReady\":true,\"dailyReminder\":true,\"reminderHour\":19,\"timeZone\":\"America/La_Paz\"}"));
        }
        session(practiced);
        Instant now = Instant.now();
        // 18:30 en La Paz (UTC-4): todavía no.
        Clock before = Clock.fixed(now.atZone(ZoneOffset.ofHours(-4)).withHour(18).withMinute(30).toInstant(), ZoneOffset.UTC);
        new DailyReminderScheduler(jdbc, notifications, queue, before).run();
        verify(gateway, never()).send(argThat(target -> target.endpoint().equals(waitingEndpoint)), any());

        // 23:59 de hoy en La Paz: la sesión creada ahora cuenta como práctica de hoy.
        Clock after = Clock.fixed(now.atZone(ZoneOffset.ofHours(-4)).withHour(23).withMinute(59).toInstant(), ZoneOffset.UTC);
        DailyReminderScheduler scheduler = new DailyReminderScheduler(jdbc, notifications, queue, after);
        scheduler.run();
        scheduler.run();
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(gateway, timeout(5000).times(1)).send(argThat(target -> target.endpoint().equals(waitingEndpoint)), payload.capture());
        verify(gateway, never()).send(argThat(target -> target.endpoint().equals(practicedEndpoint)), any());
        assertThat(new String(payload.getValue(), StandardCharsets.UTF_8)).contains("Hora de practicar");
    }

    @Test void onlyAdministratorsCanBroadcast() throws Exception {
        UserAccount regular = user();
        subscribe(regular, fcm());
        String body = "{\"title\":\"Mantenimiento\",\"body\":\"Hoy a las 22:00 la plataforma estará en mantenimiento.\"}";
        mvc.perform(post("/api/admin/notifications/broadcast").header("Authorization", auth(regular))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());

        UserAccount admin = users.saveAndFlush(new UserAccount("Admin", "Push", UUID.randomUUID() + "@example.test",
                encoder.encode("TestPassword123!"), UserRole.ADMIN));
        mvc.perform(post("/api/admin/notifications/broadcast").header("Authorization", auth(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"body\":\"x\"}")).andExpect(status().isBadRequest());
        String regularEndpoint = jdbc.queryForObject("SELECT endpoint FROM push_subscriptions WHERE user_id = ?", String.class, regular.getId());
        mvc.perform(post("/api/admin/notifications/broadcast").header("Authorization", auth(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.devices").isNumber());
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(gateway, timeout(5000)).send(argThat(target -> target.endpoint().equals(regularEndpoint)), payload.capture());
        assertThat(new String(payload.getValue(), StandardCharsets.UTF_8)).contains("Mantenimiento");
        Integer audited = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'NOTIFICATION_BROADCAST' AND actor_email = ?",
                Integer.class, admin.getEmail());
        assertThat(audited).isEqualTo(1);
    }
}
