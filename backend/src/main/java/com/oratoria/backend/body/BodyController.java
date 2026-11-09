package com.oratoria.backend.body;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/sessions/{id}/body-analysis")
public class BodyController {
    private final BodyAnalysisService service;
    public BodyController(BodyAnalysisService service) { this.service = service; }
    @GetMapping
    JsonNode get(Principal principal, @PathVariable UUID id) { return service.get(principal.getName(), id); }
    @PostMapping
    JsonNode analyze(Principal principal, @PathVariable UUID id, @Valid @RequestBody BodyRequest request) {
        return service.analyze(principal.getName(), id, request);
    }
    public record BodyRequest(@AssertTrue boolean consent,
            @jakarta.validation.constraints.NotNull @Size(min = 1, max = 12) List<@jakarta.validation.constraints.NotNull @Size(max = 600000) String> frames) {
        @Override public String toString() { return "BodyRequest[consent=" + consent + ", frames=" + (frames == null ? 0 : frames.size()) + "]"; }
    }
}
