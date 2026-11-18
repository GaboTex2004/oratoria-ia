package com.oratoria.backend.user.preferences;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/preferences")
public class PreferencesController {
    private final PreferencesService service;

    public PreferencesController(PreferencesService service) { this.service = service; }

    @GetMapping
    PreferencesService.PreferencesView get(Principal principal) { return service.get(principal.getName()); }

    @PutMapping
    PreferencesService.PreferencesView update(Principal principal, @Valid @RequestBody PreferencesService.PreferencesUpdate request) {
        return service.update(principal.getName(), request);
    }
}
