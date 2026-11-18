package com.oratoria.backend.sprint4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.oratoria.backend.session.entity.TrainingSession;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.training.TrainingType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ReportIntegrationTests extends Sprint4TestSupport {
    @Autowired TrainingSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;

    @Test void reportCoversOnlyTheUsersSessionsInThePeriod() throws Exception {
        var user = free();
        sessions.saveAndFlush(new TrainingSession(user, TrainingType.PRESENTATION));
        sessions.saveAndFlush(new TrainingSession(user, TrainingType.INTERVIEW));
        var old = sessions.saveAndFlush(new TrainingSession(user, TrainingType.DEBATE));
        jdbc.update("UPDATE training_sessions SET created_at = now() - interval '60 days' WHERE id = ?", old.getId());
        sessions.saveAndFlush(new TrainingSession(free(), TrainingType.PRESENTATION));

        mvc.perform(get("/api/reports/performance?days=30").header("Authorization", auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("Ana Prueba"))
                .andExpect(jsonPath("$.periodDays").value(30))
                .andExpect(jsonPath("$.totalSessions").value(2))
                .andExpect(jsonPath("$.evaluatedSessions").value(0))
                .andExpect(jsonPath("$.averageScore").value(nullValue()))
                .andExpect(jsonPath("$.sessions", hasSize(2)))
                .andExpect(jsonPath("$.nextStep").isNotEmpty());
        mvc.perform(get("/api/reports/performance?days=0").header("Authorization", auth(user)))
                .andExpect(jsonPath("$.totalSessions").value(3));
    }

    @Test void pdfIsALegibleDownloadForTheUser() throws Exception {
        var user = free();
        sessions.saveAndFlush(new TrainingSession(user, TrainingType.PRESENTATION));
        var response = mvc.perform(get("/api/reports/performance.pdf?days=30").header("Authorization", auth(user)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=\"reporte-oratoria-")))
                .andReturn().getResponse();
        byte[] pdf = response.getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("Ana Prueba").contains("Reporte de desempeño").contains("Últimos 30 días");
    }

    @Test void unsupportedPeriodIsRejected() throws Exception {
        mvc.perform(get("/api/reports/performance?days=45").header("Authorization", auth(free()))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/performance.pdf?days=-1").header("Authorization", auth(free()))).andExpect(status().isBadRequest());
    }

    @Test void reportRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/reports/performance.pdf?days=30")).andExpect(status().isUnauthorized());
    }
}
