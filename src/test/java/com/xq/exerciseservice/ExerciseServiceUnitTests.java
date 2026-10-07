package com.xq.exerciseservice;

import static com.xq.exerciseservice.exercise.ExerciseApi.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import java.util.List;

import com.xq.exerciseservice.exercise.ExerciseService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class ExerciseServiceUnitTests {
    @Test
    void requestModelPreservesIndividualSetValues() {
        ExerciseSetRequest set = new ExerciseSetRequest(2, new BigDecimal("65.00"), 8);
        CreateExerciseLogRequest request = new CreateExerciseLogRequest(
                " Bench Press ", null, List.of(set));

        assertEquals(" Bench Press ", request.exerciseName());
        assertEquals(new BigDecimal("65.00"), request.sets().getFirst().weightKg());
        assertEquals(8, request.sets().getFirst().reps());
    }
}
