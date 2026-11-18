package com.oratoria.backend.progress.controller;

import com.oratoria.backend.progress.service.AdaptiveRecommendationEngine;
import com.oratoria.backend.progress.service.RecommendationService;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService service;
    public RecommendationController(RecommendationService service) { this.service = service; }
    @GetMapping
    AdaptiveRecommendationEngine.Recommendation get(Principal principal) { return service.recommend(principal.getName()); }
}
