package com.oratoria.backend.progress.controller;

import com.oratoria.backend.progress.dto.ProgressSummaryResponse;
import com.oratoria.backend.progress.service.ProgressService;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {
    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping("/summary")
    ProgressSummaryResponse getSummary(Principal principal) {
        return progressService.getSummary(principal != null ? principal.getName() : null);
    }
}
