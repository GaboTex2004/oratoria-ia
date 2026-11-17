package com.oratoria.backend.admin.plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/plans")
public class PlanAdminController {
    private final PlanAdminService service;
    public PlanAdminController(PlanAdminService service) { this.service = service; }

    @GetMapping List<PlanAdminService.PlanAdminView> list() { return service.list(); }
    @PutMapping("/{code}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@PathVariable String code, @Valid @RequestBody PlanUpdate update) { service.update(code, update); }

    public record PlanUpdate(@NotBlank @Size(max = 100) String name, @NotNull @DecimalMin("0") BigDecimal price,
            boolean active, @NotNull List<@Valid EntitlementUpdate> entitlements) {}
    public record EntitlementUpdate(@NotBlank String code, boolean enabled, Integer limit, boolean unlimited) {}
}
