package com.oratoria.backend.analysis.controller;

import com.oratoria.backend.analysis.dto.SpeechAnalysisResponse;
import com.oratoria.backend.analysis.service.SpeechAnalysisService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions/{sessionId}/analysis")
public class SpeechAnalysisController {
    private final SpeechAnalysisService analysisService;

    public SpeechAnalysisController(SpeechAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping
    SpeechAnalysisResponse getAnalysis(Principal principal, @PathVariable UUID sessionId) {
        return analysisService.getAnalysisForUserSession(principal.getName(), sessionId);
    }

    @PostMapping("/retry")
    SpeechAnalysisResponse retryAnalysis(Principal principal, @PathVariable UUID sessionId) {
        return analysisService.retryAnalysisForUserSession(principal.getName(), sessionId);
    }
}
