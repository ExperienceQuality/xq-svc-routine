package com.xq.routineservice.routine;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class RoutineApi {
  public record RoutineRequest(
      @NotBlank @Size(max = 120) String name, @Size(max = 2000) String notes) {}

  public record ExerciseRequest(@NotBlank @Size(max = 120) String name) {}

  public record SessionRequest(
      @NotBlank @Size(max = 120) String name,
      @NotNull @Min(0) Integer position,
      @NotEmpty @Size(max = 50) List<@Valid ExerciseRequest> exercises) {}

  public record RoutineResponse(
      String id,
      String name,
      String notes,
      Instant archivedAt,
      long version,
      Instant createdAt,
      Instant updatedAt,
      List<SessionResponse> sessions) {}

  public record RoutineListItem(
      String id,
      String name,
      String notes,
      Instant archivedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {}

  public record SessionResponse(
      String id, String name, int position, List<ExerciseResponse> exercises) {}

  public record ExerciseResponse(String name, int position) {}

  public record PageResponse<T>(List<T> items, String nextCursor) {}

  public record RecordPage(List<Object> items, String nextCursor) {}
}
