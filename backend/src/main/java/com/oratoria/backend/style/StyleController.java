package com.oratoria.backend.style;

import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class StyleController {
    private final StyleService service;

    public StyleController(StyleService service) { this.service = service; }

    @GetMapping("/api/styles")
    StyleService.StylesResponse list(Principal principal) { return service.list(principal.getName()); }

    @PutMapping("/api/styles/selected")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void select(Principal principal, @RequestBody StyleSelection selection) {
        service.select(principal.getName(), selection.style());
    }

    @GetMapping("/api/sessions/{id}/style-feedback")
    StyleService.StyleFeedbackView feedback(Principal principal, @PathVariable UUID id) {
        return service.feedback(principal.getName(), id);
    }

    public record StyleSelection(@Size(max = 40) String style) {}
}
