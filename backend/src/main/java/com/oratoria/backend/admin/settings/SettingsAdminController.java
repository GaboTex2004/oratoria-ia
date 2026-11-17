package com.oratoria.backend.admin.settings;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class SettingsAdminController {
    private final SystemSettingsService settings;
    public SettingsAdminController(SystemSettingsService settings) { this.settings = settings; }

    @GetMapping("/api/admin/settings") Map<String, String> all() { return settings.all(); }

    @PutMapping("/api/admin/settings") @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@RequestBody Map<String, String> changes) { settings.update(changes); }

    @GetMapping("/api/system/announcement") Map<String, String> announcement() { return Map.of("message", settings.announcement()); }
}
