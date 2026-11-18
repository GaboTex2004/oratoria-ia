package com.oratoria.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.oratoria.backend.exercise.repository.ExerciseRepository;
import com.oratoria.backend.session.entity.TrainingSession;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminReportIntegrationTests extends AdminTestSupport {
    @Autowired TrainingSessionRepository sessions;
    @Autowired ExerciseRepository exercises;
    @Autowired JdbcTemplate jdbc;

    @Test void completedExercisesReportCountsCompletedSessions() throws Exception {
        var exercise = exercises.findByCode("RITMO_PAUSAS").orElseThrow();
        var session = sessions.saveAndFlush(new TrainingSession(user(UserRole.USER), exercise, exercise.getTrainingType()));
        jdbc.update("UPDATE training_sessions SET status = 'COMPLETED', completed_at = now(), duration_seconds = 60 WHERE id = ?", session.getId());
        mvc.perform(get("/api/admin/reports/exercises?days=30").header("Authorization", auth(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodDays").value(30))
                .andExpect(jsonPath("$.totalCompleted", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[?(@.title=='Pausas con intención')].completed", everyItem(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.items[?(@.title=='Pausas con intención')].category", contains("RITMO")));
    }

    @Test void difficultiesReportListsCriteria() throws Exception {
        mvc.perform(get("/api/admin/reports/difficulties?days=0").header("Authorization", auth(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria").isArray())
                .andExpect(jsonPath("$.evaluations").isNumber())
                .andExpect(jsonPath("$.frequentFillers").isArray());
    }

    @Test void bothReportsExportToPdf() throws Exception {
        String admin = auth(UserRole.ADMIN);
        for (String[] report : new String[][]{{"exercises", "Ejercicios completados"}, {"difficulties", "Dificultades frecuentes"}}) {
            byte[] pdf = mvc.perform(get("/api/admin/reports/" + report[0] + ".pdf?days=90").header("Authorization", admin))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1)).contains(report[1]).contains("Últimos 90 días");
        }
    }

    @Test void invalidPeriodAndNonAdminsAreRejected() throws Exception {
        mvc.perform(get("/api/admin/reports/exercises?days=12").header("Authorization", auth(UserRole.ADMIN))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/reports/difficulties").header("Authorization", auth(UserRole.USER))).andExpect(status().isForbidden());
    }
}
