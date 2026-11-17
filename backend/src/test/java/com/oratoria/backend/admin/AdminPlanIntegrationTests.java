package com.oratoria.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.oratoria.backend.exercise.repository.ExerciseRepository;
import com.oratoria.backend.subscription.entity.PlanCode;
import com.oratoria.backend.subscription.entity.UsageLimit;
import com.oratoria.backend.subscription.repository.PlanEntitlementRepository;
import com.oratoria.backend.subscription.repository.PlanRepository;
import com.oratoria.backend.user.entity.UserRole;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;

class AdminPlanIntegrationTests extends AdminTestSupport {
    @Autowired PlanRepository planRepository;
    @Autowired PlanEntitlementRepository entitlements;
    @Autowired ExerciseRepository exercises;

    String freeUpdate(String limitsJson) {
        return "{\"name\":\"Gratis\",\"price\":0,\"active\":true,\"entitlements\":[" + limitsJson + "]}";
    }

    @Test void listsFreeAndPremiumWithEntitlements() throws Exception {
        mvc.perform(get("/api/admin/plans").header("Authorization", auth(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItems("FREE", "PREMIUM")))
                .andExpect(jsonPath("$[?(@.code=='FREE')].entitlements[?(@.code=='SESSIONS_PER_MONTH')].kind", contains("LIMIT")))
                .andExpect(jsonPath("$[?(@.code=='FREE')].entitlements[?(@.code=='CAMERA_ANALYSIS')].kind", contains("FEATURE")));
    }

    @Test void updatingFreeSessionLimitChangesQuota() throws Exception {
        mvc.perform(put("/api/admin/plans/FREE").header("Authorization", auth(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(freeUpdate("{\"code\":\"SESSIONS_PER_MONTH\",\"enabled\":true,\"limit\":2,\"unlimited\":false}")))
                .andExpect(status().isNoContent());
        String user = auth(UserRole.USER);
        String exerciseId = exercises.findByActiveTrueOrderByCreatedAtAsc().get(0).getId().toString();
        String body = "{\"exerciseId\":\"" + exerciseId + "\"}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/sessions").header("Authorization", user).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/sessions").header("Authorization", user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test void negativeLimitRejected() throws Exception {
        mvc.perform(put("/api/admin/plans/FREE").header("Authorization", auth(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(freeUpdate("{\"code\":\"SESSIONS_PER_MONTH\",\"enabled\":true,\"limit\":-1,\"unlimited\":false}")))
                .andExpect(status().isBadRequest());
    }

    @Test void unknownEntitlementRejected() throws Exception {
        mvc.perform(put("/api/admin/plans/FREE").header("Authorization", auth(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(freeUpdate("{\"code\":\"NO_EXISTE\",\"enabled\":true,\"limit\":1,\"unlimited\":false}")))
                .andExpect(status().isBadRequest());
    }

    @Test void unlimitedClearsValue() throws Exception {
        mvc.perform(put("/api/admin/plans/FREE").header("Authorization", auth(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(freeUpdate("{\"code\":\"SIMULATIONS_PER_MONTH\",\"enabled\":true,\"limit\":9,\"unlimited\":true}")))
                .andExpect(status().isNoContent());
        var free = planRepository.findByCode(PlanCode.FREE).orElseThrow();
        var limit = entitlements.findByPlanIdAndUsageLimit(free.getId(), UsageLimit.SIMULATIONS_PER_MONTH).orElseThrow();
        assertThat(limit.isUnlimited()).isTrue();
        assertThat(limit.getLimitValue()).isNull();
    }

    @Test void freePlanCannotBeDeactivated() throws Exception {
        mvc.perform(put("/api/admin/plans/FREE").header("Authorization", auth(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Gratis\",\"price\":0,\"active\":false,\"entitlements\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test void priceEditSurvivesRestart() throws Exception {
        mvc.perform(put("/api/admin/plans/PREMIUM").header("Authorization", auth(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Premium\",\"price\":79.50,\"active\":true,\"entitlements\":[]}"))
                .andExpect(status().isNoContent());
        plans.run(new DefaultApplicationArguments());
        assertThat(planRepository.findByCode(PlanCode.PREMIUM).orElseThrow().getPrice()).isEqualByComparingTo(new BigDecimal("79.50"));
    }

    @Test void nonAdminGets403() throws Exception {
        mvc.perform(get("/api/admin/plans").header("Authorization", auth(UserRole.USER))).andExpect(status().isForbidden());
    }
}
