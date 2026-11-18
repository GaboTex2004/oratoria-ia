package com.oratoria.backend.admin;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.oratoria.backend.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuditIntegrationTests extends AdminTestSupport {
    @Test void loginSuccessAndFailureAreRecorded() throws Exception {
        var user = user(UserRole.USER);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"TestPassword123!\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"Equivocada123!\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/audit").param("actor", user.getEmail()).header("Authorization", auth(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].action", containsInAnyOrder("LOGIN_SUCCESS", "LOGIN_FAILED")))
                .andExpect(jsonPath("$.items[0].actionLabel").isNotEmpty())
                .andExpect(jsonPath("$.items[0].occurredAt").isNotEmpty());
    }

    @Test void adminChangesAreRecordedWithTheActor() throws Exception {
        var admin = user(UserRole.ADMIN);
        String token = "Bearer " + jwt.createToken(admin);
        mvc.perform(put("/api/admin/settings").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"announcement.message\":\"Mantenimiento el sábado\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/audit").param("actor", admin.getEmail()).param("action", "SETTINGS_UPDATED").header("Authorization", token))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].actorEmail").value(admin.getEmail()))
                .andExpect(jsonPath("$.items[0].entityType").value("settings"))
                .andExpect(jsonPath("$.items[0].actionLabel").value("Parámetros del sistema actualizados"));
    }

    @Test void failedAdminChangesAreNotRecorded() throws Exception {
        var admin = user(UserRole.ADMIN);
        String token = "Bearer " + jwt.createToken(admin);
        mvc.perform(put("/api/admin/settings").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"audio.max-duration-seconds\":\"5\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/audit").param("actor", admin.getEmail()).header("Authorization", token))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test void registrationIsRecorded() throws Exception {
        String email = "nuevo-" + System.nanoTime() + "@example.test";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Nueva\",\"lastName\":\"Cuenta\",\"email\":\"" + email + "\",\"password\":\"Segura123!\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/admin/audit").param("actor", email).header("Authorization", auth(UserRole.ADMIN)))
                .andExpect(jsonPath("$.items[0].action").value("REGISTER"));
    }

    @Test void adminPasswordResetIsRecordedWithTheAffectedUser() throws Exception {
        var admin = user(UserRole.ADMIN);
        var student = user(UserRole.USER);
        String token = "Bearer " + jwt.createToken(admin);
        mvc.perform(post("/api/admin/users/" + student.getId() + "/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"NuevaClaveSegura123!\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/audit").param("actor", admin.getEmail()).header("Authorization", token))
                .andExpect(jsonPath("$.items[0].action").value("USER_PASSWORD_RESET"))
                .andExpect(jsonPath("$.items[0].entityId").value(student.getId().toString()))
                .andExpect(jsonPath("$.items[0].detail", not(containsString("NuevaClave"))));
    }

    @Test void backupCreationRecordsTheBackupId() throws Exception {
        var admin = user(UserRole.ADMIN);
        String token = "Bearer " + jwt.createToken(admin);
        String body = mvc.perform(post("/api/admin/backups").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/admin/audit").param("actor", admin.getEmail()).param("action", "BACKUP_CREATED").header("Authorization", token))
                .andExpect(jsonPath("$.items[0].entityId").value(id));
    }

    @Test void actionCatalogIsAvailableForFilters() throws Exception {
        mvc.perform(get("/api/admin/audit/actions").header("Authorization", auth(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItems("LOGIN_FAILED", "BACKUP_RESTORED", "PLAN_UPDATED")));
    }

    @Test void onlyAdminsCanReadTheLog() throws Exception {
        mvc.perform(get("/api/admin/audit").header("Authorization", auth(UserRole.USER))).andExpect(status().isForbidden());
    }
}
