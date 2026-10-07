package com.xq.exerciseservice.exercise;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public final class ExerciseApi {
    private ExerciseApi() { }

    public record CreateExerciseLogRequest(
            @NotBlank String exerciseName,
            Instant loggedAt,
            @NotEmpty List<@Valid ExerciseSetRequest> sets) { }

    public record ExerciseSetRequest(
            @Min(1) @Max(32767) int setNumber,
            @NotNull @DecimalMin("0.00") BigDecimal weightKg,
            @Min(1) @Max(32767) int reps) { }

    public record ExerciseLogResponse(
            Long id,
            String exerciseName,
            Instant loggedAt,
            List<ExerciseSetResponse> sets,
            BigDecimal highestSetVolumeKg) { }

    public record ExerciseSetResponse(
            Long id,
            int setNumber,
            BigDecimal weightKg,
            int reps,
            BigDecimal volumeKg) { }

    public record ExerciseSummaryResponse(
            String exerciseName,
            long logCount,
            Instant lastLoggedAt) { }
}
