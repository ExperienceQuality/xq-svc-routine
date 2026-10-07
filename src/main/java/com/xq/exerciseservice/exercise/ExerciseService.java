package com.xq.exerciseservice.exercise;

import static com.xq.exerciseservice.exercise.ExerciseApi.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.xq.exerciseservice.shared.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExerciseService {
    private final JdbcClient jdbc;

    public ExerciseService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ExerciseLogResponse create(CreateExerciseLogRequest request) {
        String exerciseName = request.exerciseName().trim();
        Instant loggedAt = request.loggedAt() == null ? Instant.now() : request.loggedAt();
        Long logId = jdbc.sql("""
                insert into exercise_logs(exercise_name, logged_at)
                values (:exerciseName, :loggedAt)
                returning id
                """)
                .param("exerciseName", exerciseName)
                .param("loggedAt", loggedAt.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();

        for (ExerciseSetRequest set : request.sets()) {
            jdbc.sql("""
                    insert into exercise_sets(exercise_log_id, set_number, weight_kg, reps)
                    values (:logId, :setNumber, :weightKg, :reps)
                    """)
                    .param("logId", logId)
                    .param("setNumber", set.setNumber())
                    .param("weightKg", set.weightKg())
                    .param("reps", set.reps())
                    .update();
        }
        return get(logId);
    }

    @Transactional(readOnly = true)
    public ExerciseLogResponse get(long id) {
        ExerciseLogResponse response = findLogs("where el.id = :id", java.util.Map.of("id", id), null).stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("EXERCISE_LOG_NOT_FOUND", "Exercise log was not found"));
        return response;
    }

    @Transactional(readOnly = true)
    public List<ExerciseLogResponse> list(String exerciseName, String sort, int limit) {
        if (limit < 1 || limit > 100) {
            throw ApiException.badRequest("INVALID_LIMIT", "limit must be between 1 and 100");
        }
        if (!sort.equals("loggedAt") && !sort.equals("highestSetVolume")) {
            throw ApiException.badRequest("INVALID_SORT", "sort must be loggedAt or highestSetVolume");
        }
        String predicate = exerciseName == null || exerciseName.isBlank()
                ? ""
                : "where lower(el.exercise_name) = lower(:exerciseName)";
        java.util.Map<String, Object> params = exerciseName == null || exerciseName.isBlank()
                ? java.util.Map.of()
                : java.util.Map.of("exerciseName", exerciseName.trim());
        return findLogs(predicate, params, sort + " limit " + limit);
    }

    @Transactional(readOnly = true)
    public List<ExerciseSummaryResponse> listDistinctExercises() {
        return jdbc.sql("""
                select exercise_name, count(*) as log_count, max(logged_at) as last_logged_at
                from exercise_logs
                group by exercise_name
                order by lower(exercise_name), exercise_name
                """)
                .query((rs, row) -> new ExerciseSummaryResponse(
                        rs.getString("exercise_name"), rs.getLong("log_count"), instant(rs, "last_logged_at")))
                .list();
    }

    private List<ExerciseLogResponse> findLogs(String predicate, java.util.Map<String, Object> params, String ordering) {
        String orderBy = "highestSetVolume".equals(ordering == null ? null : ordering.split(" ")[0])
                ? "order by max(es.weight_kg * es.reps) desc, el.logged_at desc, el.id desc"
                : "order by el.logged_at desc, el.id desc";
        String limit = ordering == null || ordering.isBlank() ? "" : " limit " + ordering.substring(ordering.lastIndexOf(' ') + 1);
        List<LogRow> logs = jdbc.sql("""
                select el.id, el.exercise_name, el.logged_at
                from exercise_logs el
                left join exercise_sets es on es.exercise_log_id = el.id
                %s
                group by el.id, el.exercise_name, el.logged_at
                %s
                %s
                """.formatted(predicate, orderBy, limit))
                .params(params)
                .query((rs, row) -> new LogRow(rs.getLong("id"), rs.getString("exercise_name"), instant(rs, "logged_at")))
                .list();
        return logs.stream().map(this::withSets).toList();
    }

    private ExerciseLogResponse withSets(LogRow log) {
        List<ExerciseSetResponse> sets = jdbc.sql("""
                select id, set_number, weight_kg, reps
                from exercise_sets
                where exercise_log_id = :logId
                order by set_number
                """)
                .param("logId", log.id())
                .query((rs, row) -> {
                    BigDecimal weight = rs.getBigDecimal("weight_kg");
                    int reps = rs.getInt("reps");
                    return new ExerciseSetResponse(rs.getLong("id"), rs.getInt("set_number"), weight, reps,
                            volume(weight, reps));
                })
                .list();
        BigDecimal highest = sets.stream().map(ExerciseSetResponse::volumeKg)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO.setScale(2));
        return new ExerciseLogResponse(log.id(), log.exerciseName(), log.loggedAt(), sets, highest);
    }

    private static BigDecimal volume(BigDecimal weight, int reps) {
        return weight.multiply(BigDecimal.valueOf(reps)).setScale(2, RoundingMode.HALF_UP);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, java.time.OffsetDateTime.class).toInstant();
    }

    private record LogRow(Long id, String exerciseName, Instant loggedAt) { }
}
