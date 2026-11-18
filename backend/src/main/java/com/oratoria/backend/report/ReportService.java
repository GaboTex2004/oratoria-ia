package com.oratoria.backend.report;

import com.oratoria.backend.auth.exception.InvalidCredentialsException;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.CriterionScore;
import com.oratoria.backend.progress.dto.ProgressSummaryResponse.HistoryItem;
import com.oratoria.backend.progress.service.ProgressService;
import com.oratoria.backend.progress.service.RecommendationService;
import com.oratoria.backend.shared.exception.BadRequestException;
import com.oratoria.backend.simulation.repository.SimulationSessionRepository;
import com.oratoria.backend.subscription.service.FeatureAccessService;
import com.oratoria.backend.user.entity.UserAccount;
import com.oratoria.backend.user.repository.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consolidates sessions, metrics, evaluations and evolution for a period into one report (CU-23). */
@Service
public class ReportService {
    static final Set<Integer> PERIODS = Set.of(0, 7, 30, 90, 365);
    static final int SESSION_ROWS = 50;
    static final Map<String, String> CRITERIA = new LinkedHashMap<>();
    static {
        CRITERIA.put("CLARITY", "Claridad");
        CRITERIA.put("COHERENCE", "Coherencia");
        CRITERIA.put("ORGANIZATION", "Organización");
        CRITERIA.put("ARGUMENTATION", "Argumentación");
    }

    private final UserAccountRepository users;
    private final ProgressService progress;
    private final RecommendationService recommendations;
    private final SimulationSessionRepository simulations;
    private final FeatureAccessService features;

    public ReportService(UserAccountRepository users, ProgressService progress, RecommendationService recommendations,
                         SimulationSessionRepository simulations, FeatureAccessService features) {
        this.users = users; this.progress = progress; this.recommendations = recommendations;
        this.simulations = simulations; this.features = features;
    }

    @Transactional
    public PerformanceReport build(String email, int days) {
        if (!PERIODS.contains(days)) throw new BadRequestException("Periodo de reporte inválido. Usa 7, 30, 90, 365 o 0 (todo el historial).");
        UserAccount user = users.findByEmailIgnoreCase(email).filter(UserAccount::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);
        Instant now = Instant.now();
        Instant from = days == 0 ? Instant.EPOCH : now.minus(Duration.ofDays(days));
        ProgressSummaryResponse summary = progress.getSummary(email);
        List<HistoryItem> inPeriod = summary.history().stream().filter(item -> !item.createdAt().isBefore(from))
                .sorted(Comparator.comparing(HistoryItem::createdAt)).toList();
        List<HistoryItem> evaluated = inPeriod.stream()
                .filter(item -> Boolean.TRUE.equals(item.sufficientEvidence()) && item.overallScore() != null).toList();

        Double average = evaluated.isEmpty() ? null : round(evaluated.stream().mapToDouble(HistoryItem::overallScore).average().orElse(0));
        Double best = evaluated.stream().map(HistoryItem::overallScore).max(Double::compare).orElse(null);
        Double change = evaluated.size() < 2 ? null
                : round(evaluated.getLast().overallScore() - evaluated.getFirst().overallScore());
        long seconds = inPeriod.stream().map(HistoryItem::durationSeconds).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();

        List<CriterionAverage> criteria = new ArrayList<>();
        CRITERIA.forEach((code, label) -> {
            List<Double> scores = evaluated.stream().flatMap(item -> item.criteriaScores() == null ? java.util.stream.Stream.<CriterionScore>empty() : item.criteriaScores().stream())
                    .filter(score -> code.equals(score.code()) && score.score() != null).map(CriterionScore::score).toList();
            if (!scores.isEmpty()) {
                criteria.add(new CriterionAverage(code, label, round(scores.stream().mapToDouble(Double::doubleValue).average().orElse(0)), scores.size()));
            }
        });
        List<String> strengths = criteria.stream().filter(item -> item.average() >= 75).map(CriterionAverage::label).toList();
        List<String> improvements = criteria.stream().filter(item -> item.average() < 60).map(CriterionAverage::label).toList();

        List<SessionRow> rows = inPeriod.reversed().stream().limit(SESSION_ROWS).map(item -> new SessionRow(item.createdAt(),
                item.exerciseTitle(), item.trainingType() == null ? null : item.trainingType().name(), item.durationSeconds(),
                Boolean.TRUE.equals(item.sufficientEvidence()) ? item.overallScore() : null, item.wordsPerMinute(),
                item.fillerWordsTotal())).toList();

        return new PerformanceReport(user.getDisplayName(), user.getEmail(), features.summary(user.getId()).plan(), days,
                days == 0 ? null : from, now, inPeriod.size(), evaluated.size(), Math.round(seconds / 60.0),
                simulations.countByUserIdAndCreatedAtGreaterThanEqual(user.getId(), from),
                average, best, change, criteria, strengths, improvements, rows,
                recommendations.recommend(email).reason());
    }

    static String periodLabel(int days) {
        return days == 0 ? "Todo el historial" : "Últimos " + days + " días";
    }

    private static Double round(double value) { return Math.round(value * 10) / 10.0; }

    public record PerformanceReport(String userName, String email, String plan, int periodDays, Instant periodStart,
                                    Instant generatedAt, int totalSessions, int evaluatedSessions, long practiceMinutes,
                                    long simulations, Double averageScore, Double bestScore, Double scoreChange,
                                    List<CriterionAverage> criteria, List<String> strengths, List<String> areasToImprove,
                                    List<SessionRow> sessions, String nextStep) {}

    public record CriterionAverage(String code, String label, double average, int evaluations) {}

    public record SessionRow(Instant createdAt, String exercise, String trainingType, Integer durationSeconds,
                             Double score, Double wordsPerMinute, Integer fillerWords) {}
}
