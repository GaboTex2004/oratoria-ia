package com.oratoria.backend.simulation.controller;

import com.oratoria.backend.simulation.service.SimulationPrivacyService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulations")
public class SimulationPrivacyController {
    private final SimulationPrivacyService service;
    public SimulationPrivacyController(SimulationPrivacyService service) { this.service = service; }
    @DeleteMapping("/{id}/data") @ResponseStatus(HttpStatus.ACCEPTED)
    void erase(Principal principal, @PathVariable UUID id) { service.erase(principal.getName(), id); }
}
