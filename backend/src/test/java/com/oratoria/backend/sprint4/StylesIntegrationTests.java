package com.oratoria.backend.sprint4;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.oratoria.backend.exercise.repository.ExerciseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class StylesIntegrationTests extends Sprint4TestSupport {
    @Autowired ExerciseRepository exercises;

    String narrativeExercise() { return exercises.findByCode("ESTILO_NARRATIVO").orElseThrow().getId().toString(); }

    static String id(String json) { return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"); }

    @Test void listsTheFourStylesWithTheirExercises() throws Exception {
        mvc.perform(get("/api/styles").header("Authorization", auth(free())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.disclaimer", containsString("identidad")))
                .andExpect(jsonPath("$.styles[*].code", contains("PERSUASIVO", "NARRATIVO", "DINAMICO", "ACADEMICO")))
                .andExpect(jsonPath("$.styles[0].traits", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.styles[?(@.code=='DINAMICO')].exercises[*].communicationStyle", everyItem(is("DINAMICO"))))
                .andExpect(jsonPath("$.styles[?(@.code=='ACADEMICO')].exercises.length()", contains(2)));
        mvc.perform(get("/api/styles").header("Authorization", auth(premium()))).andExpect(jsonPath("$.locked").value(false));
    }

    @Test void selectingAStyleIsStoredInThePreferences() throws Exception {
        String user = auth(premium());
        mvc.perform(put("/api/styles/selected").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"style\":\"NARRATIVO\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/styles").header("Authorization", user)).andExpect(jsonPath("$.selected").value("NARRATIVO"));
        mvc.perform(get("/api/users/me/preferences").header("Authorization", user)).andExpect(jsonPath("$.preferredStyle").value("NARRATIVO"));
        mvc.perform(put("/api/styles/selected").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"style\":\"NO_EXISTE\"}")).andExpect(status().isBadRequest());
    }

    @Test void freeUsersCannotSelectOrPracticeStyles() throws Exception {
        String user = auth(free());
        mvc.perform(put("/api/styles/selected").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"style\":\"NARRATIVO\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/sessions").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"exerciseId\":\"" + narrativeExercise() + "\"}")).andExpect(status().isForbidden());
    }

    @Test void styledSessionKeepsItsStyleAndWaitsForTheEvaluation() throws Exception {
        String user = auth(premium());
        String response = mvc.perform(post("/api/sessions").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + narrativeExercise() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.communicationStyle").value("NARRATIVO"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/sessions/" + id(response) + "/style-feedback").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.style").value("NARRATIVO"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test void styleCanBeChosenForAFreeFormPractice() throws Exception {
        String user = auth(premium());
        mvc.perform(post("/api/sessions").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainingType\":\"PRESENTATION\",\"communicationStyle\":\"ACADEMICO\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.communicationStyle").value("ACADEMICO"));
        mvc.perform(post("/api/sessions").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":\"" + narrativeExercise() + "\",\"communicationStyle\":\"ACADEMICO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void plainSessionsHaveNoStyleFeedback() throws Exception {
        String user = auth(premium());
        String response = mvc.perform(post("/api/sessions").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"trainingType\":\"PRESENTATION\"}")).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/sessions/" + id(response) + "/style-feedback").header("Authorization", user)).andExpect(status().isBadRequest());
    }

    @Test void anotherUserCannotReadStyleFeedback() throws Exception {
        String response = mvc.perform(post("/api/sessions").header("Authorization", auth(premium())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"exerciseId\":\"" + narrativeExercise() + "\"}")).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/sessions/" + id(response) + "/style-feedback").header("Authorization", auth(premium())))
                .andExpect(status().isForbidden());
    }
}
