package com.oratoria.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oratoria.backend.admin.monitoring.AiMonitoringClient;
import com.oratoria.backend.admin.monitoring.AiMonitoringClient.AiMonitoringException;
import com.oratoria.backend.admin.monitoring.MonitoringService;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class MonitoringIntegrationTests extends AdminTestSupport {
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AiMonitoringClient ai;

    String admin() { return "Bearer " + jwt.createToken(user(UserRole.ADMIN)); }

    /** Un análisis de voz que lleva 30 minutos en proceso. */
    void stuckAnalysis(UserAccount owner) {
        UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO training_sessions (id, created_at, status, training_type, user_id) VALUES (?, now(), 'PROCESSING', 'PRESENTATION', ?)",
                session, owner.getId());
        jdbc.update("""
                INSERT INTO speech_analyses (id, attempt_count, created_at, retryable, schema_version, status, updated_at, session_id, started_at)
                VALUES (?, 1, now() - interval '31 minutes', true, '1', 'PROCESSING', now() - interval '30 minutes', ?, now() - interval '30 minutes')""",
                UUID.randomUUID(), session);
    }

    @Test void onlyAdministratorsSeeTheMonitor() throws Exception {
        mvc.perform(get("/api/admin/monitoring").header("Authorization", "Bearer " + jwt.createToken(user(UserRole.USER))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/monitoring/analysis").header("Authorization", "Bearer " + jwt.createToken(user(UserRole.USER))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ai);
    }

    @Test void theSnapshotHasRealMetricsAndRuleBasedAlerts() throws Exception {
        String token = admin();
        stuckAnalysis(user(UserRole.USER));
        mvc.perform(get("/api/admin/monitoring").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics[?(@.key == 'servicio_base_datos')].value").value("UP"))
                .andExpect(jsonPath("$.metrics[?(@.key == 'analisis_trabados')].value").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.alerts[?(@.code == 'ANALISIS_STUCK')]").exists())
                .andExpect(jsonPath("$.metrics[?(@.key == 'push_dispositivos')]").exists());
    }

    @Test void theLocalModelReceivesOnlyMetricsAndAlertsAndItsAnswerIsReturned() throws Exception {
        UserAccount adminUser = user(UserRole.ADMIN);
        String token = "Bearer " + jwt.createToken(adminUser);
        // La bitácora se confirma en su propia transacción: se cuenta solo lo de este administrador.
        Integer auditedBefore = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'MONITORING_ANALYSIS' AND actor_email = ?",
                Integer.class, adminUser.getEmail());
        stuckAnalysis(user(UserRole.USER));
        when(ai.analyze(anyMap(), anyList(), any())).thenReturn(new AiMonitoringClient.Analysis("ATENCION",
                "Hay análisis trabados.", List.of(new AiMonitoringClient.Finding("Análisis trabados", "Uno lleva 30 minutos.", List.of("analisis_trabados"))),
                List.of("Revisar el servicio de IA."), "Porque el servicio de IA no terminó.", false, "ollama", "qwen2.5:1.5b"));

        mvc.perform(post("/api/admin/monitoring/analysis").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"¿Por qué hay análisis trabados?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.summary").value("Hay análisis trabados."))
                .andExpect(jsonPath("$.analysis.model").value("qwen2.5:1.5b"))
                .andExpect(jsonPath("$.analysis.findings[0].evidence[0]").value("analisis_trabados"))
                .andExpect(jsonPath("$.metricsUsed.analisis_trabados").exists());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metrics = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MonitoringService.Alert>> alerts = ArgumentCaptor.forClass(List.class);
        verify(ai).analyze(metrics.capture(), alerts.capture(), eq("¿Por qué hay análisis trabados?"));
        // Sin datos personales: solo cifras y estados con nombres de métrica.
        assertThat(metrics.getValue().values()).allMatch(value -> value instanceof Number || "UP".equals(value) || "DOWN".equals(value));
        assertThat(metrics.getValue().toString()).doesNotContain(adminUser.getEmail());
        assertThat(alerts.getValue()).extracting(MonitoringService.Alert::code).contains("ANALISIS_STUCK");
        Integer audited = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'MONITORING_ANALYSIS' AND actor_email = ?",
                Integer.class, adminUser.getEmail());
        assertThat(audited).isEqualTo(auditedBefore + 1);
    }

    @Test void aBusyModelIsReportedWithoutAnInventedAnswer() throws Exception {
        when(ai.analyze(anyMap(), anyList(), isNull())).thenThrow(new AiMonitoringException("LOCAL_MODEL_BUSY",
                "El modelo local está ocupado con una práctica. Inténtalo en unos segundos.", true));
        mvc.perform(post("/api/admin/monitoring/analysis").header("Authorization", admin()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LOCAL_MODEL_BUSY"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.analysis").doesNotExist());
    }

    @Test void pendingPracticeEvaluationsKeepPriorityOverTheAssistant() throws Exception {
        UserAccount owner = user(UserRole.USER);
        UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO training_sessions (id, created_at, status, training_type, user_id) VALUES (?, now(), 'COMPLETED', 'PRESENTATION', ?)",
                session, owner.getId());
        jdbc.update("""
                INSERT INTO speech_evaluations (id, attempt_count, created_at, retryable, schema_version, status, updated_at, session_id)
                VALUES (?, 0, now(), true, '1', 'PENDING', now(), ?)""", UUID.randomUUID(), session);
        mvc.perform(post("/api/admin/monitoring/analysis").header("Authorization", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRACTICES_IN_PROGRESS"));
        verifyNoInteractions(ai);
    }

    @Test void longQuestionsAreRejected() throws Exception {
        mvc.perform(post("/api/admin/monitoring/analysis").header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"" + "x".repeat(301) + "\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(ai);
    }
}
