package com.oratoria.backend.evaluation.controller;

import com.oratoria.backend.evaluation.dto.SpeechEvaluationResponse;
import com.oratoria.backend.evaluation.service.SpeechEvaluationService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions/{sessionId}/evaluation")
public class SpeechEvaluationController {
    private final SpeechEvaluationService evaluationService;

    public SpeechEvaluationController(SpeechEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping
    public SpeechEvaluationResponse getSessionEvaluation(
            @PathVariable UUID sessionId,
            Principal principal) {
        return evaluationService.getEvaluationForUserSession(
                principal != null ? principal.getName() : null,
                sessionId
        );
    }

    @PostMapping("/retry")
    public SpeechEvaluationResponse retrySessionEvaluation(
            @PathVariable UUID sessionId,
            Principal principal) {
        return evaluationService.retryEvaluationForUserSession(
                principal != null ? principal.getName() : null,
                sessionId
        );
    }
}
