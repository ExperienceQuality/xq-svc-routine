package com.xq.exerciseservice.exercise;

import static com.xq.exerciseservice.exercise.ExerciseApi.*;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exercise-logs")
public final class ExerciseController {
    private final ExerciseService service;

    public ExerciseController(ExerciseService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ExerciseLogResponse> create(@Valid @RequestBody CreateExerciseLogRequest request) {
        ExerciseLogResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/exercise-logs/" + response.id())).body(response);
    }

    @GetMapping("/{exerciseLogId}")
    ExerciseLogResponse get(@PathVariable long exerciseLogId) {
        return service.get(exerciseLogId);
    }

    @GetMapping
    List<ExerciseLogResponse> list(
            @RequestParam(required = false) String exerciseName,
            @RequestParam(defaultValue = "loggedAt") String sort,
            @RequestParam(defaultValue = "20") int limit) {
        return service.list(exerciseName, sort, limit);
    }

    @GetMapping("/exercises")
    List<ExerciseSummaryResponse> listDistinctExercises() {
        return service.listDistinctExercises();
    }
}
