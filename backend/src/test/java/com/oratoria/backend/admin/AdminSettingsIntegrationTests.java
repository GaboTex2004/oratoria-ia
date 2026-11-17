package com.oratoria.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.oratoria.backend.session.service.AudioStorageService;
import com.oratoria.backend.user.entity.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class AdminSettingsIntegrationTests extends AdminTestSupport {
    @Autowired AudioStorageService storage;

    void save(String admin, String json) throws Exception {
        mvc.perform(put("/api/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNoContent());
    }

    String register() {
        return "{\"firstName\":\"Nueva\",\"lastName\":\"Cuenta\",\"email\":\"" + UUID.randomUUID() + "@example.test\","
                + "\"password\":\"TestPassword123!\",\"acceptTerms\":true}";
    }

    @Test void defaultsAreReturned() throws Exception {
        mvc.perform(get("/api/admin/settings").header("Authorization", auth(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['registration.enabled']").value("true"))
                .andExpect(jsonPath("$['audio.max-duration-seconds']").value(String.valueOf(storage.getMaxDurationSeconds())))
                .andExpect(jsonPath("$['announcement.message']").value(""));
    }

    @Test void closingRegistrationBlocksRegister() throws Exception {
        String admin = auth(UserRole.ADMIN);
        save(admin, "{\"registration.enabled\":\"false\"}");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register()))
                .andExpect(status().isForbidden());
        save(admin, "{\"registration.enabled\":\"true\"}");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register()))
                .andExpect(status().isCreated());
    }

    @Test void invalidDurationRejectedAndNothingSaved() throws Exception {
        String admin = auth(UserRole.ADMIN);
        mvc.perform(put("/api/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"announcement.message\":\"Hola\",\"audio.max-duration-seconds\":\"5\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"audio.max-duration-seconds\":\"abc\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/settings").header("Authorization", admin))
                .andExpect(jsonPath("$['announcement.message']").value(""));
    }

    @Test void unknownKeyRejected() throws Exception {
        mvc.perform(put("/api/admin/settings").header("Authorization", auth(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"otra.cosa\":\"1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void announcementVisibleToUsers() throws Exception {
        save(auth(UserRole.ADMIN), "{\"announcement.message\":\"Mantenimiento el viernes\"}");
        mvc.perform(get("/api/system/announcement").header("Authorization", auth(UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mantenimiento el viernes"));
    }

    @Test void audioLimitUsesSetting() throws Exception {
        save(auth(UserRole.ADMIN), "{\"audio.max-duration-seconds\":\"90\"}");
        assertThat(storage.getMaxDurationSeconds()).isEqualTo(90);
    }

    @Test void recordingLimitIsPublishedToUsers() throws Exception {
        save(auth(UserRole.ADMIN), "{\"audio.max-duration-seconds\":\"120\"}");
        mvc.perform(get("/api/system/limits").header("Authorization", auth(UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audioMaxDurationSeconds").value(120));
    }

    @Test void nonAdminGets403() throws Exception {
        mvc.perform(get("/api/admin/settings").header("Authorization", auth(UserRole.USER))).andExpect(status().isForbidden());
    }
}
