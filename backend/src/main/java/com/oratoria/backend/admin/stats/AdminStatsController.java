package com.oratoria.backend.admin.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminStatsController {
    private final AdminStatsService stats;
    public AdminStatsController(AdminStatsService stats) { this.stats = stats; }

    @GetMapping("/api/admin/stats") AdminStatsService.AdminStats stats() { return stats.stats(); }
}
