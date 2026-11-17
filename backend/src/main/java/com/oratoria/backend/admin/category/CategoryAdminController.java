package com.oratoria.backend.admin.category;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/categories")
public class CategoryAdminController {
    private final CategoryAdminService service;
    public CategoryAdminController(CategoryAdminService service) { this.service = service; }

    @GetMapping List<CategoryAdminService.CategoryRow> list() { return service.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    void create(@Valid @RequestBody CategoryUpdate update) { service.create(update); }
    @PutMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@PathVariable UUID id, @Valid @RequestBody CategoryUpdate update) { service.update(id, update); }

    public record CategoryUpdate(@NotBlank @Pattern(regexp = "[A-Z0-9_]{3,40}") String code,
            @NotBlank @Size(max = 80) String name, @Size(max = 300) String description, boolean active) {}
}
