package com.oratoria.backend.sprint4;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.oratoria.backend.session.entity.TrainingSession;
import com.oratoria.backend.session.repository.TrainingSessionRepository;
import com.oratoria.backend.subscription.entity.Feature;
import com.oratoria.backend.subscription.repository.PlanEntitlementRepository;
import com.oratoria.backend.training.TrainingType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class PlanFeatureIntegrationTests extends Sprint4TestSupport {
    @Autowired TrainingSessionRepository sessions;
    @Autowired PlanEntitlementRepository entitlements;

    static final String FRAME = "{\"consent\":true,\"frames\":[\"data:image/jpeg;base64,AAAA\"]}";

    @Test void featuresEndpointReflectsTheCurrentPlan() throws Exception {
        mvc.perform(get("/api/subscriptions/current/features").header("Authorization", auth(free())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.features[?(@.code=='BASIC_VOICE_ANALYSIS')].enabled", contains(true)))
                .andExpect(jsonPath("$.features[?(@.code=='CAMERA_ANALYSIS')].enabled", contains(false)))
                .andExpect(jsonPath("$.features[?(@.code=='CAMERA_ANALYSIS')].label", contains("Análisis con cámara")));
        mvc.perform(get("/api/subscriptions/current/features").header("Authorization", auth(premium())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.features[?(@.enabled==false)]", hasSize(0)));
    }

    @Test void freeUserCannotRunCameraAnalysis() throws Exception {
        var user = free();
        var session = sessions.saveAndFlush(new TrainingSession(user, TrainingType.PRESENTATION));
        mvc.perform(post("/api/sessions/" + session.getId() + "/body-analysis").header("Authorization", auth(user))
                        .contentType(MediaType.APPLICATION_JSON).content(FRAME))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Premium")));
    }

    @Test void premiumUserReachesCameraAnalysis() throws Exception {
        var user = premium();
        var session = sessions.saveAndFlush(new TrainingSession(user, TrainingType.PRESENTATION));
        // The local pose service is not running in tests, so reaching it proves the plan check passed.
        mvc.perform(post("/api/sessions/" + session.getId() + "/body-analysis").header("Authorization", auth(user))
                        .contentType(MediaType.APPLICATION_JSON).content(FRAME))
                .andExpect(status().isBadRequest());
    }

    @Test void freeUserCannotStartImprovisationAndKeepsQuota() throws Exception {
        String free = auth(free());
        mvc.perform(post("/api/sessions").header("Authorization", free).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainingType\":\"IMPROVISATION\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/subscriptions/current/usage").header("Authorization", free))
                .andExpect(jsonPath("$.practices.used").value(0));
        mvc.perform(post("/api/sessions").header("Authorization", auth(premium())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainingType\":\"IMPROVISATION\"}"))
                .andExpect(status().isCreated());
    }

    @Test void freeUserCannotStartImprovisationSimulation() throws Exception {
        mvc.perform(post("/api/simulations").header("Authorization", auth(free())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"guided-improvisation\",\"difficulty\":\"BASIC\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/simulations").header("Authorization", auth(free())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioId\":\"job-interview\",\"difficulty\":\"BASIC\"}"))
                .andExpect(status().isCreated());
    }

    @Test void adminTogglesTakeEffectForFreeUsers() throws Exception {
        var freePlan = plans.freePlan();
        entitlements.findByPlanIdAndFeature(freePlan.getId(), Feature.IMPROVISATION).orElseThrow().setEnabled(true);
        entitlements.flush();
        mvc.perform(post("/api/sessions").header("Authorization", auth(free())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainingType\":\"IMPROVISATION\"}"))
                .andExpect(status().isCreated());
    }
}
