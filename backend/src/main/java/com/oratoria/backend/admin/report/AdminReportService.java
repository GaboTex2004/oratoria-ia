package com.oratoria.backend.admin.report;

import com.oratoria.backend.shared.exception.BadRequestException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Platform-wide reports for administrators: completed exercises and frequent difficulties ("Reportes del sistema"). */
@Service
public class AdminReportService {
    static final Set<Integer> PERIODS = Set.of(0, 7, 30, 90, 365);
    static final double LOW_SCORE = 60;
    static final int TOP_FILLERS = 10;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AdminReportService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Transactional(readOnly = true)
    public ExercisesReport exercises(int days) {
        Timestamp from = from(days);
        List<ExerciseRow> items = jdbc.query("""
                SELECT e.title, e.category, e.difficulty, count(*) AS completed, count(DISTINCT t.user_id) AS users,
                       round(avg(t.overall_score)::numeric, 1) AS average_score, coalesce(sum(t.duration_seconds), 0) AS seconds
                FROM training_sessions t JOIN exercises e ON e.id = t.exercise_id
                WHERE t.status = 'COMPLETED' AND t.created_at >= ?
                GROUP BY e.id, e.title, e.category, e.difficulty
                ORDER BY completed DESC, e.title""", (rs, i) -> new ExerciseRow(rs.getString("title"), rs.getString("category"),
                rs.getString("difficulty"), rs.getLong("completed"), rs.getLong("users"),
                rs.getBigDecimal("average_score") == null ? null : rs.getBigDecimal("average_score").doubleValue(),
                Math.round(rs.getLong("seconds") / 60.0)), from);
        long freePractice = count("SELECT count(*) FROM training_sessions WHERE status = 'COMPLETED' AND exercise_id IS NULL AND created_at >= ?", from);
        long total = items.stream().mapToLong(ExerciseRow::completed).sum() + freePractice;
        return new ExercisesReport(days, Instant.now(), total, freePractice,
                count("SELECT count(DISTINCT user_id) FROM training_sessions WHERE status = 'COMPLETED' AND created_at >= ?", from), items);
    }

    @Transactional(readOnly = true)
    public DifficultiesReport difficulties(int days) {
        Timestamp from = from(days);
        Map<String, double[]> totals = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        List<String> evaluations = jdbc.queryForList("""
                SELECT e.criteria_scores_json FROM speech_evaluations e JOIN training_sessions t ON t.id = e.session_id
                WHERE e.status = 'COMPLETED' AND e.sufficient_evidence AND e.criteria_scores_json IS NOT NULL AND t.created_at >= ?""",
                String.class, from);
        for (String raw : evaluations) {
            JsonNode criteria = parse(raw);
            if (criteria == null || !criteria.isArray()) continue;
            for (JsonNode criterion : criteria) {
                JsonNode score = criterion.get("score");
                String code = criterion.path("code").asString("");
                if (code.isBlank() || score == null || !score.isNumber()) continue;
                double[] sum = totals.computeIfAbsent(code, key -> new double[3]);
                sum[0] += score.asDouble();
                sum[1]++;
                if (score.asDouble() < LOW_SCORE) sum[2]++;
                labels.putIfAbsent(code, criterion.path("label").asString(code));
            }
        }
        List<CriterionRow> criteria = totals.entrySet().stream().map(entry -> {
            double[] value = entry.getValue();
            return new CriterionRow(entry.getKey(), labels.get(entry.getKey()), Math.round(value[0] / value[1] * 10) / 10.0,
                    (long) value[1], (long) value[2], Math.round(value[2] / value[1] * 1000) / 10.0);
        }).sorted(Comparator.comparingDouble(CriterionRow::averageScore)).toList();

        Map<String, Long> fillers = new HashMap<>();
        for (String raw : jdbc.queryForList("""
                SELECT a.filler_counts_json FROM speech_analyses a JOIN training_sessions t ON t.id = a.session_id
                WHERE a.filler_counts_json IS NOT NULL AND t.created_at >= ?""", String.class, from)) {
            JsonNode counts = parse(raw);
            if (counts == null || !counts.isObject()) continue;
            for (String filler : counts.propertyNames()) {
                JsonNode value = counts.get(filler);
                if (value.isNumber()) fillers.merge(filler, value.asLong(), Long::sum);
            }
        }
        List<FillerRow> frequentFillers = fillers.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_FILLERS).map(entry -> new FillerRow(entry.getKey(), entry.getValue())).toList();
        return new DifficultiesReport(days, Instant.now(), evaluations.size(), criteria, frequentFillers);
    }

    private JsonNode parse(String raw) {
        try { return json.readTree(raw); } catch (RuntimeException exception) { return null; }
    }

    private static Timestamp from(int days) {
        if (!PERIODS.contains(days)) throw new BadRequestException("Periodo de reporte inválido. Usa 7, 30, 90, 365 o 0 (todo el historial).");
        return Timestamp.from(days == 0 ? Instant.EPOCH : Instant.now().minus(Duration.ofDays(days)));
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    public record ExercisesReport(int periodDays, Instant generatedAt, long totalCompleted, long freePractice,
                                  long activeUsers, List<ExerciseRow> items) {}

    public record ExerciseRow(String title, String category, String difficulty, long completed, long users,
                              Double averageScore, long practiceMinutes) {}

    public record DifficultiesReport(int periodDays, Instant generatedAt, int evaluations, List<CriterionRow> criteria,
                                     List<FillerRow> frequentFillers) {}

    public record CriterionRow(String code, String label, double averageScore, long evaluations, long lowScores, double lowScoreRate) {}

    public record FillerRow(String filler, long count) {}
}
