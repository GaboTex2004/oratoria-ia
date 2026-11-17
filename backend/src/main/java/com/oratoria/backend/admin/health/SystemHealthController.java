package com.oratoria.backend.admin.health;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemHealthController {
    private final SystemHealthService health;
    public SystemHealthController(SystemHealthService health) { this.health = health; }

    @GetMapping("/api/admin/system/health") List<SystemHealthService.ModuleStatus> health() { return health.check(); }
}
