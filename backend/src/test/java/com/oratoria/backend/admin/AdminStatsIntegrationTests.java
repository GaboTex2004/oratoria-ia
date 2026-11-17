package com.oratoria.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.oratoria.backend.exercise.entity.Exercise;
import com.oratoria.backend.exercise.repository.ExerciseRepository;
import com.oratoria.backend.session.entity.TrainingSession;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.training.TrainingType;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminStatsIntegrationTests extends AdminTestSupport {
    @Autowired ExerciseRepository exercises;
    @Autowired TrainingSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;

    String stats(String admin) throws Exception {
        return mvc.perform(get("/api/admin/stats").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    void evaluation(TrainingSession session, String criteriaJson) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO speech_evaluations (id, attempt_count, created_at, updated_at, criteria_scores_json, retryable,
                    schema_version, status, session_id) VALUES (?, 1, ?, ?, ?, false, '1', 'COMPLETED', ?)""",
                UUID.randomUUID(), now, now, criteriaJson, session.getId());
    }

    @Test void statsHaveExpectedShapeWithoutEvaluations() throws Exception {
        mvc.perform(get("/api/admin/stats").header("Authorization", auth(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isNumber())
                .andExpect(jsonPath("$.activeUsers").isNumber())
                .andExpect(jsonPath("$.premiumUsers").isNumber())
                .andExpect(jsonPath("$.practicesThisMonth").isNumber())
                .andExpect(jsonPath("$.simulationsThisMonth").isNumber())
                .andExpect(jsonPath("$.lastSixMonths.length()").value(6))
                .andExpect(jsonPath("$.topExercises").isArray())
                .andExpect(jsonPath("$.frequentDifficulties").isArray());
    }

    @Test void countsPracticesAndTopExercises() throws Exception {
        String admin = auth(UserRole.ADMIN);
        int before = JsonPath.read(stats(admin), "$.practicesThisMonth");
        UserAccount user = user(UserRole.USER);
        Exercise exercise = exercises.save(new Exercise("EJ_STATS_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "Ejercicio muy usado", "D", "P", TrainingType.PRESENTATION, 60));
        for (int i = 0; i < 40; i++) sessions.save(new TrainingSession(user, exercise, TrainingType.PRESENTATION));
        sessions.flush();
        String body = stats(admin);
        assertThat((int) JsonPath.read(body, "$.practicesThisMonth")).isEqualTo(before + 40);
        assertThat(JsonPath.<List<String>>read(body, "$.topExercises[*].title")).contains("Ejercicio muy usado");
        assertThat((int) JsonPath.read(body, "$.activeUsers")).isGreaterThanOrEqualTo(1);
    }

    @Test void frequentDifficultiesAveragesCriteria() throws Exception {
        UserAccount user = user(UserRole.USER);
        TrainingSession first = sessions.save(new TrainingSession(user, TrainingType.PRESENTATION));
        TrainingSession second = sessions.save(new TrainingSession(user, TrainingType.PRESENTATION));
        sessions.flush();
        evaluation(first, "[{\"code\":\"PRUEBA_DEBIL\",\"label\":\"Prueba débil\",\"score\":0.5},{\"code\":\"SIN_NOTA\",\"label\":\"Sin nota\",\"score\":null}]");
        evaluation(second, "[{\"code\":\"PRUEBA_DEBIL\",\"label\":\"Prueba débil\",\"score\":1.5}]");
        String body = stats(auth(UserRole.ADMIN));
        assertThat(JsonPath.<String>read(body, "$.frequentDifficulties[0].code")).isEqualTo("PRUEBA_DEBIL");
        assertThat(JsonPath.<Double>read(body, "$.frequentDifficulties[0].averageScore")).isEqualTo(1.0);
        assertThat((int) JsonPath.read(body, "$.frequentDifficulties[0].evaluations")).isEqualTo(2);
        assertThat(JsonPath.<List<String>>read(body, "$.frequentDifficulties[*].code")).doesNotContain("SIN_NOTA");
    }

    @Test void nonAdminGets403() throws Exception {
        mvc.perform(get("/api/admin/stats").header("Authorization", auth(UserRole.USER))).andExpect(status().isForbidden());
    }
}
