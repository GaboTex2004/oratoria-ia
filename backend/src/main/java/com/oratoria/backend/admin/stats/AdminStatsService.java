package com.oratoria.backend.admin.stats;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatsService {
    private static final int MONTHS = 6;
    private static final int TOP_EXERCISES = 5;
    private static final int WEAK_CRITERIA = 3;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AdminStatsService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Transactional(readOnly = true)
    public AdminStats stats() {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        Timestamp monthStart = start(current);
        Timestamp thirtyDaysAgo = Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS));
        return new AdminStats(
                count("SELECT count(*) FROM app_users"),
                count("""
                        SELECT count(DISTINCT user_id) FROM (
                            SELECT user_id FROM training_sessions WHERE created_at >= ?
                            UNION SELECT user_id FROM simulation_sessions WHERE created_at >= ?) active""", thirtyDaysAgo, thirtyDaysAgo),
                count("""
                        SELECT count(DISTINCT s.user_id) FROM subscriptions s JOIN plans p ON p.id = s.plan_id
                        WHERE s.status = 'ACTIVE' AND p.code = 'PREMIUM' AND (s.ends_at IS NULL OR s.ends_at > now())"""),
                count("SELECT count(*) FROM training_sessions WHERE created_at >= ?", monthStart),
                count("SELECT count(*) FROM simulation_sessions WHERE created_at >= ?", monthStart),
                lastMonths(current),
                jdbc.query("""
                        SELECT e.title, count(*) AS total FROM training_sessions t JOIN exercises e ON e.id = t.exercise_id
                        GROUP BY e.id, e.title ORDER BY total DESC, e.title LIMIT ?""",
                        (rs, i) -> new ExerciseUsage(rs.getString("title"), rs.getLong("total")), TOP_EXERCISES),
                frequentDifficulties());
    }

    private List<MonthCount> lastMonths(YearMonth current) {
        List<MonthCount> months = new ArrayList<>();
        for (int i = MONTHS - 1; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            Timestamp from = start(month);
            Timestamp to = start(month.plusMonths(1));
            months.add(new MonthCount(month.toString(),
                    count("SELECT count(*) FROM training_sessions WHERE created_at >= ? AND created_at < ?", from, to),
                    count("SELECT count(*) FROM simulation_sessions WHERE created_at >= ? AND created_at < ?", from, to)));
        }
        return months;
    }

    /** Lowest average criterion scores across completed evaluations; criteria without a score are ignored. */
    private List<WeakCriterion> frequentDifficulties() {
        Map<String, double[]> totals = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (String raw : jdbc.queryForList("SELECT criteria_scores_json FROM speech_evaluations WHERE status = 'COMPLETED' AND criteria_scores_json IS NOT NULL", String.class)) {
            JsonNode criteria;
            try { criteria = json.readTree(raw); } catch (Exception e) { continue; }
            if (criteria == null || !criteria.isArray()) continue;
            for (JsonNode criterion : criteria) {
                JsonNode score = criterion.get("score");
                String code = criterion.path("code").asString("");
                if (code.isBlank() || score == null || !score.isNumber()) continue;
                double[] sum = totals.computeIfAbsent(code, key -> new double[2]);
                sum[0] += score.asDouble();
                sum[1]++;
                labels.putIfAbsent(code, criterion.path("label").asString(code));
            }
        }
        return totals.entrySet().stream()
                .map(entry -> new WeakCriterion(entry.getKey(), labels.get(entry.getKey()),
                        Math.round(entry.getValue()[0] / entry.getValue()[1] * 10.0) / 10.0, (long) entry.getValue()[1]))
                .sorted(Comparator.comparingDouble(WeakCriterion::averageScore).thenComparing(WeakCriterion::code))
                .limit(WEAK_CRITERIA).toList();
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static Timestamp start(YearMonth month) { return Timestamp.from(month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()); }

    public record AdminStats(long users, long activeUsers, long premiumUsers, long practicesThisMonth, long simulationsThisMonth,
            List<MonthCount> lastSixMonths, List<ExerciseUsage> topExercises, List<WeakCriterion> frequentDifficulties) {}
    public record MonthCount(String month, long practices, long simulations) {}
    public record ExerciseUsage(String title, long sessions) {}
    public record WeakCriterion(String code, String label, double averageScore, long evaluations) {}
}
