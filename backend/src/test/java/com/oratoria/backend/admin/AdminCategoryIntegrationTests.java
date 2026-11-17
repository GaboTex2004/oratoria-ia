package com.oratoria.backend.admin;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.oratoria.backend.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AdminCategoryIntegrationTests extends AdminTestSupport {

    String category(String code, boolean active) {
        return "{\"code\":\"" + code + "\",\"name\":\"Nombre " + code + "\",\"description\":\"Desc\",\"active\":" + active + "}";
    }

    String exercise(String code, String category) {
        return "{\"code\":\"" + code + "\",\"title\":\"T\",\"description\":\"D\",\"prompt\":\"P\",\"objective\":\"O\","
                + "\"category\":\"" + category + "\",\"difficulty\":\"BASIC\",\"duration\":60,\"trainingType\":\"PRESENTATION\",\"active\":true}";
    }

    String idOf(String admin, String code) throws Exception {
        String body = mvc.perform(get("/api/admin/categories").header("Authorization", admin)).andReturn().getResponse().getContentAsString();
        return JsonPath.<java.util.List<String>>read(body, "$[?(@.code=='" + code + "')].id").get(0);
    }

    @Test void listsSeededCategoriesWithExerciseCount() throws Exception {
        mvc.perform(get("/api/admin/categories").header("Authorization", auth(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItems("CLARIDAD", "RITMO", "ENTONACION", "FLUIDEZ", "ESTRUCTURA",
                        "ARGUMENTACION", "IMPROVISACION", "NO_VERBAL", "ESTILOS")))
                .andExpect(jsonPath("$[0].exercises").isNumber());
    }

    @Test void adminCreatesAndEditsCategory() throws Exception {
        String admin = auth(UserRole.ADMIN);
        mvc.perform(post("/api/admin/categories").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(category("LIDERAZGO", true))).andExpect(status().isCreated());
        String id = idOf(admin, "LIDERAZGO");
        mvc.perform(put("/api/admin/categories/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"OTRO_CODIGO\",\"name\":\"Liderazgo\",\"description\":\"Nueva\",\"active\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/categories").header("Authorization", admin))
                .andExpect(jsonPath("$[?(@.code=='LIDERAZGO')].name", contains("Liderazgo")))
                .andExpect(jsonPath("$[?(@.code=='LIDERAZGO')].active", contains(false)));
    }

    @Test void duplicateCodeIsRejected() throws Exception {
        mvc.perform(post("/api/admin/categories").header("Authorization", auth(UserRole.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(category("RITMO", true))).andExpect(status().isBadRequest());
    }

    @Test void exerciseCannotBeCreatedInInactiveCategory() throws Exception {
        String admin = auth(UserRole.ADMIN);
        mvc.perform(post("/api/admin/categories").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(category("PAUSADA", false))).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/exercises").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(exercise("EJ_PAUSADA", "PAUSADA"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/exercises").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(exercise("EJ_INEXISTENTE", "NO_EXISTE"))).andExpect(status().isBadRequest());
    }

    @Test void existingExerciseKeepsInactiveCategory() throws Exception {
        String admin = auth(UserRole.ADMIN);
        mvc.perform(post("/api/admin/categories").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(category("TEMPORAL", true))).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/exercises").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(exercise("EJ_TEMPORAL", "TEMPORAL"))).andExpect(status().isCreated());
        String categoryId = idOf(admin, "TEMPORAL");
        mvc.perform(put("/api/admin/categories/" + categoryId).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(category("TEMPORAL", false))).andExpect(status().isNoContent());
        String list = mvc.perform(get("/api/admin/exercises").header("Authorization", admin)).andReturn().getResponse().getContentAsString();
        String exerciseId = JsonPath.<java.util.List<String>>read(list, "$[?(@.code=='EJ_TEMPORAL')].id").get(0);
        mvc.perform(put("/api/admin/exercises/" + exerciseId).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(exercise("EJ_TEMPORAL", "TEMPORAL").replace("\"title\":\"T\"", "\"title\":\"Editado\"")))
                .andExpect(status().isNoContent());
    }

    @Test void nonAdminGets403() throws Exception {
        mvc.perform(get("/api/admin/categories").header("Authorization", auth(UserRole.USER))).andExpect(status().isForbidden());
    }
}
