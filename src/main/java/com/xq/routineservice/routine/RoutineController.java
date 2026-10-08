package com.xq.routineservice.routine;

import static com.xq.routineservice.routine.RoutineApi.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/routines")
public class RoutineController {
  private final RoutineService service;

  public RoutineController(RoutineService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<RoutineResponse> create(@Valid @RequestBody RoutineRequest r) {
    var x = service.create(r);
    return ResponseEntity.created(URI.create("/api/v1/routines/" + x.id()))
        .eTag(etag(x.version()))
        .body(x);
  }

  @GetMapping
  public PageResponse<RoutineListItem> list(
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "false") boolean includeArchived,
      @RequestParam(required = false) String cursor) {
    return service.list(limit, includeArchived, cursor);
  }

  @GetMapping("/{id}")
  public ResponseEntity<RoutineResponse> get(@PathVariable String id) {
    var x = service.get(id);
    return ResponseEntity.ok().eTag(etag(x.version())).body(x);
  }

  @PatchMapping("/{id}")
  public ResponseEntity<RoutineResponse> patch(
      @PathVariable String id,
      @RequestBody Map<String, Object> r,
      @RequestHeader(value = "If-Match", required = false) String match) {
    var x = service.patch(id, r, match);
    return ResponseEntity.ok().eTag(etag(x.version())).body(x);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> archive(
      @PathVariable String id, @RequestHeader(value = "If-Match", required = false) String match) {
    service.archive(id, match);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/sessions")
  public ResponseEntity<SessionResponse> add(
      @PathVariable String id,
      @Valid @RequestBody SessionRequest r,
      @RequestHeader(value = "If-Match", required = false) String match) {
    var x = service.addSession(id, r, match);
    return ResponseEntity.status(HttpStatus.CREATED).eTag(etag(service.get(id).version())).body(x);
  }

  @GetMapping("/{id}/sessions/{sid}")
  public SessionResponse session(@PathVariable String id, @PathVariable String sid) {
    return service.session(id, sid);
  }

  @PutMapping("/{id}/sessions/{sid}")
  public ResponseEntity<SessionResponse> replace(
      @PathVariable String id,
      @PathVariable String sid,
      @Valid @RequestBody SessionRequest r,
      @RequestHeader(value = "If-Match", required = false) String match) {
    var x = service.replaceSession(id, sid, r, match);
    return ResponseEntity.ok().eTag(etag(service.get(id).version())).body(x);
  }

  @DeleteMapping("/{id}/sessions/{sid}")
  public ResponseEntity<Void> delete(
      @PathVariable String id,
      @PathVariable String sid,
      @RequestHeader(value = "If-Match", required = false) String match) {
    service.deleteSession(id, sid, match);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/exercise-records")
  public RecordPage records(
      @PathVariable String id,
      @RequestParam(required = false) String sessionId,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    return service.records(id, sessionId, limit, cursor);
  }

  private static String etag(long v) {
    return "\"" + v + "\"";
  }
}
