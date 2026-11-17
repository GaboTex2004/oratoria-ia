package com.oratoria.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.oratoria.backend.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.ai-service.url=http://127.0.0.1:1")
class AdminHealthIntegrationTests extends AdminTestSupport {

    @Test void databaseIsUp() throws Exception {
        mvc.perform(get("/api/admin/system/health").header("Authorization", auth(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.module=='database')].status", contains("UP")))
                .andExpect(jsonPath("$[?(@.module=='audio-storage')].status", contains("UP")));
    }

    @Test void unreachableAiServiceIsReportedDownQuickly() throws Exception {
        String admin = auth(UserRole.ADMIN);
        long started = System.nanoTime();
        mvc.perform(get("/api/admin/system/health").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.module=='ai-service')].status", contains("DOWN")));
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(4000);
    }

    @Test void nonAdminGets403() throws Exception {
        mvc.perform(get("/api/admin/system/health").header("Authorization", auth(UserRole.USER))).andExpect(status().isForbidden());
    }
}
