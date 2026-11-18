package com.oratoria.backend.sprint4;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PreferencesIntegrationTests extends Sprint4TestSupport {
    static String body(String goal, String skills, int weeklyGoal, String style) {
        return "{\"goal\":" + goal + ",\"experienceLevel\":\"INTERMEDIATE\",\"prioritySkills\":[" + skills
                + "],\"preferredTrainingTypes\":[\"INTERVIEW\"],\"preferredStyle\":" + style + ",\"weeklyGoal\":" + weeklyGoal + "}";
    }

    @Test void defaultsAreReturnedBeforeTheFirstSave() throws Exception {
        mvc.perform(get("/api/users/me/preferences").header("Authorization", auth(free())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal").value(nullValue()))
                .andExpect(jsonPath("$.experienceLevel").value("BASIC"))
                .andExpect(jsonPath("$.weeklyGoal").value(3))
                .andExpect(jsonPath("$.prioritySkills", hasSize(0)))
                .andExpect(jsonPath("$.options.goals", hasSize(6)))
                .andExpect(jsonPath("$.options.skills[*].code", hasItem("CLARIDAD")))
                .andExpect(jsonPath("$.options.styles[*].code", hasItem("PERSUASIVO")));
    }

    @Test void savedGoalsAndPreferencesPersist() throws Exception {
        String user = auth(free());
        mvc.perform(put("/api/users/me/preferences").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"JOB_INTERVIEWS\"", "\"CLARIDAD\",\"RITMO\"", 4, "null")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me/preferences").header("Authorization", user))
                .andExpect(jsonPath("$.goal").value("JOB_INTERVIEWS"))
                .andExpect(jsonPath("$.experienceLevel").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.prioritySkills", contains("CLARIDAD", "RITMO")))
                .andExpect(jsonPath("$.preferredTrainingTypes", contains("INTERVIEW")))
                .andExpect(jsonPath("$.weeklyGoal").value(4))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test void invalidPreferencesAreRejected() throws Exception {
        String user = auth(free());
        for (String invalid : new String[]{
                body("\"JOB_INTERVIEWS\"", "\"CLARIDAD\",\"RITMO\",\"FLUIDEZ\",\"ESTRUCTURA\"", 3, "null"),
                body("\"JOB_INTERVIEWS\"", "\"NO_EXISTE\"", 3, "null"),
                body("\"JOB_INTERVIEWS\"", "\"CLARIDAD\"", 0, "null"),
                body("\"VOLAR\"", "\"CLARIDAD\"", 3, "null"),
                body("null", "\"CLARIDAD\"", 3, "\"NO_EXISTE\"")}) {
            mvc.perform(put("/api/users/me/preferences").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                    .content(invalid)).andExpect(status().isBadRequest());
        }
    }

    @Test void choosingAStyleRequiresPremium() throws Exception {
        mvc.perform(put("/api/users/me/preferences").header("Authorization", auth(free())).contentType(MediaType.APPLICATION_JSON)
                        .content(body("null", "", 3, "\"PERSUASIVO\""))).andExpect(status().isForbidden());
        mvc.perform(put("/api/users/me/preferences").header("Authorization", auth(premium())).contentType(MediaType.APPLICATION_JSON)
                        .content(body("null", "", 3, "\"PERSUASIVO\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferredStyle").value("PERSUASIVO"));
    }

    @Test void weeklyProgressCountsThisWeeksPractices() throws Exception {
        String user = auth(free());
        mvc.perform(post("/api/sessions").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"trainingType\":\"PRESENTATION\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/users/me/preferences").header("Authorization", user))
                .andExpect(jsonPath("$.practicesThisWeek").value(1));
    }
}
