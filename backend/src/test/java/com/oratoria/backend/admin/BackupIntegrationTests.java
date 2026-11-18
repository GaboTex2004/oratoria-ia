package com.oratoria.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.entity.UserRole;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class BackupIntegrationTests extends AdminTestSupport {
    @Autowired JdbcTemplate jdbc;

    static String id(String json) { return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"); }

    long usersWithEmail(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM app_users WHERE email = ?", Long.class, email);
    }

    String createBackup(String token) throws Exception {
        return id(mvc.perform(post("/api/admin/backups").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Antes de la demo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tables", greaterThan(10)))
                .andExpect(jsonPath("$.rows", greaterThan(0)))
                .andExpect(jsonPath("$.note").value("Antes de la demo"))
                .andReturn().getResponse().getContentAsString());
    }

    @Test void backupIsListedAndDownloadable() throws Exception {
        UserAccount admin = user(UserRole.ADMIN);
        String token = "Bearer " + jwt.createToken(admin);
        String id = createBackup(token);
        mvc.perform(get("/api/admin/backups").header("Authorization", token))
                .andExpect(jsonPath("$[*].id", hasItem(id)))
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].createdBy", contains(admin.getEmail())));
        String file = mvc.perform(get("/api/admin/backups/" + id + "/download").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=\"respaldo-oratoria-")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(file).contains("\"format\":\"oratoria-backup\"").contains(admin.getEmail()).doesNotContain("flyway_schema_history");
    }

    @Test void restoreBringsBackTheSavedState() throws Exception {
        UserAccount admin = user(UserRole.ADMIN);
        String token = "Bearer " + jwt.createToken(admin);
        String id = createBackup(token);
        UserAccount later = user(UserRole.USER);
        jdbc.update("UPDATE app_users SET first_name = 'Cambiado' WHERE id = ?", admin.getId());

        mvc.perform(post("/api/admin/backups/" + id + "/restore").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":\"RESTAURAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", greaterThan(0)));
        assertThat(usersWithEmail(later.getEmail())).isZero();
        assertThat(jdbc.queryForObject("SELECT first_name FROM app_users WHERE id = ?", String.class, admin.getId())).isEqualTo("Admin");
        mvc.perform(get("/api/admin/backups").header("Authorization", token)).andExpect(jsonPath("$[*].id", hasItem(id)));
        mvc.perform(get("/api/admin/audit").param("actor", admin.getEmail()).param("action", "BACKUP_RESTORED").header("Authorization", token))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test void restoreRequiresExplicitConfirmation() throws Exception {
        String token = auth(UserRole.ADMIN);
        String id = createBackup(token);
        mvc.perform(post("/api/admin/backups/" + id + "/restore").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirm\":\"si\"}")).andExpect(status().isBadRequest());
    }

    @Test void restoreFromAnUploadedFile() throws Exception {
        UserAccount admin = user(UserRole.ADMIN);
        String token = "Bearer " + jwt.createToken(admin);
        String id = createBackup(token);
        byte[] file = mvc.perform(get("/api/admin/backups/" + id + "/download").header("Authorization", token))
                .andReturn().getResponse().getContentAsByteArray();
        UserAccount later = user(UserRole.USER);
        mvc.perform(multipart("/api/admin/backups/restore").file(new MockMultipartFile("file", "respaldo.json", "application/json", file))
                        .param("confirm", "RESTAURAR").header("Authorization", token))
                .andExpect(status().isOk());
        assertThat(usersWithEmail(later.getEmail())).isZero();
        assertThat(usersWithEmail(admin.getEmail())).isOne();
    }

    @Test void invalidOrIncompatibleFilesAreRejectedWithoutTouchingData() throws Exception {
        UserAccount admin = user(UserRole.ADMIN);
        String token = "Bearer " + jwt.createToken(admin);
        mvc.perform(multipart("/api/admin/backups/restore").file(new MockMultipartFile("file", "x.json", "application/json", "no es json".getBytes()))
                .param("confirm", "RESTAURAR").header("Authorization", token)).andExpect(status().isBadRequest());
        String otherVersion = "{\"format\":\"oratoria-backup\",\"formatVersion\":1,\"schemaVersion\":\"1\",\"tables\":{}}";
        mvc.perform(multipart("/api/admin/backups/restore").file(new MockMultipartFile("file", "x.json", "application/json", otherVersion.getBytes()))
                        .param("confirm", "RESTAURAR").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("versión")));
        assertThat(usersWithEmail(admin.getEmail())).isOne();
    }

    long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }

    void expectRejectedUpload(String token, String content, String message) throws Exception {
        mvc.perform(multipart("/api/admin/backups/restore").file(new MockMultipartFile("file", "x.json", "application/json", content.getBytes()))
                        .param("confirm", "RESTAURAR").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(message)));
    }

    @Test void incompleteFilesCannotWipeTheDatabase() throws Exception {
        UserAccount admin = user(UserRole.ADMIN);
        String token = "Bearer " + jwt.createToken(admin);
        long users = count("app_users"), plans = count("plans");
        expectRejectedUpload(token, "{\"format\":\"oratoria-backup\",\"formatVersion\":1,\"schemaVersion\":\"7\",\"tables\":{}}", "falta la tabla");
        assertThat(count("app_users")).isEqualTo(users);
        assertThat(count("plans")).isEqualTo(plans);
    }

    @Test void backupsWithoutAnActiveAdminOrPlansAreRejected() throws Exception {
        UserAccount admin = user(UserRole.ADMIN);
        String token = "Bearer " + jwt.createToken(admin);
        String id = createBackup(token);
        String file = mvc.perform(get("/api/admin/backups/" + id + "/download").header("Authorization", token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        expectRejectedUpload(token, file.replace("\"role\":\"ADMIN\"", "\"role\":\"USER\""), "administrador activo");
        expectRejectedUpload(token, file.replaceAll("\"plans\":\\[[^\\]]*\\]", "\"plans\":[]"), "planes");
        assertThat(usersWithEmail(admin.getEmail())).isOne();
    }

    @Test void deletedBackupDisappears() throws Exception {
        String token = auth(UserRole.ADMIN);
        String id = createBackup(token);
        mvc.perform(delete("/api/admin/backups/" + id).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/backups/" + id + "/download").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/backups/" + UUID.randomUUID() + "/download").header("Authorization", token)).andExpect(status().isNotFound());
    }

    @Test void onlyAdminsManageBackups() throws Exception {
        mvc.perform(post("/api/admin/backups").header("Authorization", auth(UserRole.USER)).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
    }
}
