package com.oratoria.backend.session.controller;

import com.oratoria.backend.session.service.SessionPrivacyService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class SessionPrivacyController {
    private final SessionPrivacyService service;
    public SessionPrivacyController(SessionPrivacyService service) { this.service = service; }
    @DeleteMapping("/{id}/data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void delete(Principal principal, @PathVariable UUID id) { service.erase(principal.getName(), id); }
}
