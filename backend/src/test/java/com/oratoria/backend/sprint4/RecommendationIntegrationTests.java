package com.oratoria.backend.sprint4;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class RecommendationIntegrationTests extends Sprint4TestSupport {
    @Test void freeUsersGetTheBasicRecommendation() throws Exception {
        mvc.perform(get("/api/recommendations").header("Authorization", auth(free())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adaptive").value(false))
                .andExpect(jsonPath("$.exercises", hasSize(lessThanOrEqualTo(1))))
                .andExpect(jsonPath("$.upgradeHint").isNotEmpty());
    }

    @Test void premiumRecommendationFollowsTheSavedProfile() throws Exception {
        String user = auth(premium());
        mvc.perform(put("/api/users/me/preferences").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"goal\":\"PUBLIC_SPEAKING\",\"experienceLevel\":\"INTERMEDIATE\",\"prioritySkills\":[\"RITMO\"],"
                        + "\"preferredTrainingTypes\":[],\"preferredStyle\":\"NARRATIVO\",\"weeklyGoal\":3}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/recommendations").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adaptive").value(true))
                .andExpect(jsonPath("$.category").value("RITMO"))
                .andExpect(jsonPath("$.categoryName").value("Ritmo"))
                .andExpect(jsonPath("$.suggestedDifficulty").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.exercises", hasSize(3)))
                .andExpect(jsonPath("$.exercises[0].category").value("RITMO"))
                .andExpect(jsonPath("$.styleExercise.communicationStyle").value("NARRATIVO"))
                .andExpect(jsonPath("$.basedOn", hasItem(containsString("Ritmo"))));
    }
}
