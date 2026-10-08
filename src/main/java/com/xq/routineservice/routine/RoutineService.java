package com.xq.routineservice.routine;

import static com.xq.routineservice.routine.RoutineApi.*;

import com.xq.routineservice.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class RoutineService {
  private final JdbcClient jdbc;
  private final RestClient http;

  public RoutineService(
      JdbcClient jdbc, @Value("${routine.exercise-service-base-url}") String exerciseBase) {
    this.jdbc = jdbc;
    this.http =
        RestClient.builder()
            .baseUrl(exerciseBase)
            .requestInterceptor(
                (request, body, execution) -> {
                  var attrs = RequestContextHolder.getRequestAttributes();
                  if (attrs instanceof ServletRequestAttributes servlet) {
                    var namespace = servlet.getRequest().getHeader("x-node-test-kit-namespace");
                    if (namespace == null || namespace.isBlank())
                      namespace = servlet.getRequest().getHeader("X-Xq-Test-Id");
                    if (namespace != null && !namespace.isBlank()) {
                      request.getHeaders().set("x-node-test-kit-namespace", namespace);
                      request.getHeaders().set("X-Xq-Test-Id", namespace);
                    }
                  }
                  return execution.execute(request, body);
                })
            .build();
  }

  @Transactional
  public RoutineResponse create(RoutineRequest r) {
    String n = text(r.name(), "name", 120);
    String notes = r.notes() == null ? null : text(r.notes(), "notes", 2000);
    String id =
        jdbc.sql("insert into routine.routines(name,notes) values (:n,:notes) returning id")
            .param("n", n)
            .param("notes", notes)
            .query((rs, row) -> rs.getObject("id", UUID.class))
            .single()
            .toString();
    return get(id);
  }

  @Transactional(readOnly = true)
  public RoutineResponse get(String id) {
    routine(id);
    return aggregate(id, true);
  }

  @Transactional(readOnly = true)
  public PageResponse<RoutineListItem> list(int limit, boolean archived, String cursor) {
    checkLimit(limit);
    Cursor c = decode(cursor);
    if (c != null && c.archived() != archived)
      throw ApiException.badRequest("INVALID_CURSOR", "Cursor does not match filters");
    String filter = archived ? "" : "and archived_at is null";
    String more = c == null ? "" : "and (updated_at,id)<(:updated,cast(:cursorId as uuid))";
    var q =
        jdbc.sql(
                "select id,name,notes,archived_at,version,created_at,updated_at from"
                    + " routine.routines where true "
                    + filter
                    + " "
                    + more
                    + " order by updated_at desc,id desc limit :lim")
            .param("lim", limit + 1);
    if (c != null)
      q.param("updated", java.sql.Timestamp.from(c.updated())).param("cursorId", c.id());
    var rows =
        q.query(
                (rs, i) ->
                    new RoutineListItem(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("notes"),
                        instant(rs, "archived_at"),
                        rs.getLong("version"),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at")))
            .list();
    boolean next = rows.size() > limit;
    var out = rows.subList(0, Math.min(limit, rows.size()));
    return new PageResponse<>(
        out,
        next ? encode(new Cursor(archived, out.getLast().updatedAt(), out.getLast().id())) : null);
  }

  @Transactional
  public RoutineResponse patch(String id, Map<String, Object> r, String etag) {
    long expected = ifMatch(etag);
    routine(id);
    claim(id, expected);
    if (!Set.of("name", "notes").containsAll(r.keySet()))
      throw ApiException.validation("Unknown property", "body");
    if (r.isEmpty()) throw ApiException.validation("Request must contain a property", "body");
    StringBuilder sql = new StringBuilder("update routine.routines set ");
    Map<String, Object> params = new HashMap<>();
    params.put("id", id);
    List<String> sets = new ArrayList<>();
    if (r.containsKey("name")) {
      if (!(r.get("name") instanceof String n))
        throw ApiException.validation("value is required", "name");
      sets.add("name=:name");
      params.put("name", text(n, "name", 120));
    }
    if (r.containsKey("notes")) {
      Object n = r.get("notes");
      if (n != null && !(n instanceof String))
        throw ApiException.validation("value is invalid", "notes");
      sets.add("notes=:notes");
      params.put("notes", n == null ? null : text((String) n, "notes", 2000));
    }
    sets.add("updated_at=now()");
    jdbc.sql(sql.append(String.join(",", sets)).append(" where id=cast(:id as uuid)").toString())
        .params(params)
        .update();
    return get(id);
  }

  @Transactional
  public void clearNotes(String id, String etag) {
    long expected = ifMatch(etag);
    routine(id);
    claim(id, expected);
    jdbc.sql("update routine.routines set notes=null,updated_at=now() where id=cast(:id as uuid)")
        .param("id", id)
        .update();
  }

  @Transactional
  public void archive(String id, String etag) {
    long expected = ifMatch(etag);
    routine(id);
    claim(id, expected);
    jdbc.sql(
            "update routine.routines set archived_at=now(),updated_at=now() where id=cast(:id as"
                + " uuid)")
        .param("id", id)
        .update();
  }

  @Transactional
  public SessionResponse addSession(String rid, SessionRequest r, String etag) {
    long expected = ifMatch(etag);
    ensureMutable(rid, expected);
    validateExercises(r.exercises());
    int count = count(rid);
    if (count >= 50)
      throw ApiException.validation("routine cannot contain more than 50 sessions", "sessions");
    int pos = Math.min(r.position(), count);
    shift(rid, pos, 1);
    String sid =
        jdbc.sql(
                "insert into routine.routine_sessions(routine_id,name,position) values (cast(:rid"
                    + " as uuid),:n,:p) returning id")
            .param("rid", rid)
            .param("n", text(r.name(), "name", 120))
            .param("p", pos)
            .query((rs, row) -> rs.getObject("id", UUID.class))
            .single()
            .toString();
    insertExercises(sid, r.exercises());
    return session(sid);
  }

  @Transactional
  public SessionResponse replaceSession(String rid, String sid, SessionRequest r, String etag) {
    long expected = ifMatch(etag);
    ensureMutable(rid, expected);
    validateExercises(r.exercises());
    sessionRow(rid, sid);
    int count = count(rid), target = Math.min(r.position(), count - 1);
    jdbc.sql(
            "update routine.routine_sessions set position=position+1000 where routine_id=cast(:rid"
                + " as uuid)")
        .param("rid", rid)
        .update();
    var rows =
        new ArrayList<>(
            jdbc.sql(
                    "select id from routine.routine_sessions where routine_id=cast(:rid as uuid)"
                        + " order by position")
                .param("rid", rid)
                .query(String.class)
                .list());
    rows.remove(sid);
    rows.add(target, sid);
    String name = text(r.name(), "name", 120);
    for (int i = 0; i < rows.size(); i++)
      jdbc.sql(
              "update routine.routine_sessions set position=:p,name=case when id=cast(:sid as uuid)"
                  + " then :name else name end where id=cast(:id as uuid)")
          .param("p", i)
          .param("sid", sid)
          .param("name", name)
          .param("id", rows.get(i))
          .update();
    jdbc.sql("delete from routine.routine_session_exercises where session_id=cast(:sid as uuid)")
        .param("sid", sid)
        .update();
    insertExercises(sid, r.exercises());
    return session(sid);
  }

  @Transactional
  public void deleteSession(String rid, String sid, String etag) {
    long expected = ifMatch(etag);
    ensureMutable(rid, expected);
    var old = sessionRow(rid, sid);
    jdbc.sql("delete from routine.routine_sessions where id=cast(:id as uuid)")
        .param("id", sid)
        .update();
    jdbc.sql(
            "update routine.routine_sessions set position=position-1 where routine_id=cast(:rid as"
                + " uuid) and position>:p")
        .param("rid", rid)
        .param("p", old.position())
        .update();
  }

  @Transactional(readOnly = true)
  public SessionResponse session(String rid, String sid) {
    routine(rid);
    return session(sessionRow(rid, sid).id());
  }

  @Transactional(readOnly = true)
  public RecordPage records(String rid, String sid, int limit, String cursor) {
    if (sid == null || sid.isBlank())
      throw ApiException.validation("sessionId is required", "sessionId");
    checkLimit(limit);
    routine(rid);
    var routine = aggregate(rid, true);
    session(rid, sid);
    int offset = recordOffset(cursor, rid, sid, limit, routine.version());
    List<Object> all = new ArrayList<>();
    for (var e :
        jdbc.sql(
                "select exercise_name from routine.routine_session_exercises where"
                    + " session_id=cast(:sid as uuid) order by position")
            .param("sid", sid)
            .query(String.class)
            .list()) {
      try {
        var response =
            http.get()
                .uri(
                    uri ->
                        uri.path("/api/v1/exercise-logs")
                            .queryParam("exerciseName", e)
                            .queryParam("limit", 100)
                            .build())
                .exchange(
                    (req, res) -> {
                      var status = res.getStatusCode();
                      var headers = res.getHeaders();
                      if (!status.is2xxSuccessful())
                        return ResponseEntity.status(status).headers(headers).build();
                      return ResponseEntity.status(status)
                          .headers(headers)
                          .body(res.bodyTo(Object.class));
                    });
        if (response.getStatusCode().value() == 429) throw downstream(response.getHeaders());
        if (!response.getStatusCode().is2xxSuccessful())
          throw new ApiException(
              HttpStatus.SERVICE_UNAVAILABLE,
              "EXERCISE_SERVICE_UNAVAILABLE",
              "Exercise service unavailable",
              null,
              response.getHeaders().getFirst("Retry-After"));
        Object body = response.getBody();
        if (body instanceof Map<?, ?> m && m.get("items") instanceof List<?> l) all.addAll(l);
        else if (body instanceof List<?> l) all.addAll(l);
        else
          throw new ApiException(
              HttpStatus.BAD_GATEWAY,
              "DOWNSTREAM_BAD_RESPONSE",
              "Exercise service returned malformed data");
      } catch (ApiException x) {
        throw x;
      } catch (RestClientResponseException x) {
        if (x.getStatusCode().value() == 429)
          throw new ApiException(
              HttpStatus.SERVICE_UNAVAILABLE,
              "EXERCISE_SERVICE_UNAVAILABLE",
              "Exercise service unavailable",
              null,
              x.getResponseHeaders() == null
                  ? null
                  : x.getResponseHeaders().getFirst("Retry-After"));
        throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "EXERCISE_SERVICE_UNAVAILABLE",
            "Exercise service unavailable");
      } catch (Exception x) {
        throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "EXERCISE_SERVICE_UNAVAILABLE",
            "Exercise service unavailable");
      }
    }
    int end = Math.min(offset + limit, all.size());
    return new RecordPage(
        all.subList(offset, end),
        end < all.size() ? cursor(rid, sid, limit, routine.version(), end) : null);
  }

  private void insertExercises(String sid, List<ExerciseRequest> es) {
    int i = 0;
    for (var e : es)
      jdbc.sql(
              "insert into routine.routine_session_exercises(session_id,position,exercise_name)"
                  + " values (cast(:sid as uuid),:p,:n)")
          .param("sid", sid)
          .param("p", i++)
          .param("n", text(e.name(), "exercises", 120))
          .update();
  }

  private void validateExercises(List<ExerciseRequest> es) {
    Set<String> s = new HashSet<>();
    for (int i = 0; i < es.size(); i++) {
      String n = text(es.get(i).name(), "exercises[" + i + "].name", 120);
      if (!s.add(n.toLowerCase(Locale.ROOT)))
        throw ApiException.validation("duplicate exercise name", "exercises");
    }
  }

  private void shift(String rid, int at, int by) {
    jdbc.sql(
            "update routine.routine_sessions set position=position+1000 where routine_id=cast(:rid"
                + " as uuid) and position>=:p")
        .param("rid", rid)
        .param("p", at)
        .update();
    jdbc.sql(
            "update routine.routine_sessions set position=position-1000+:by where"
                + " routine_id=cast(:rid as uuid) and position>=:p")
        .param("rid", rid)
        .param("p", at + 1000)
        .param("by", by)
        .update();
  }

  private void ensureMutable(String id, long e) {
    routine(id);
    if (jdbc.sql("select archived_at from routine.routines where id=cast(:id as uuid)")
            .param("id", id)
            .query(Instant.class)
            .optional()
            .orElse(null)
        != null)
      throw new ApiException(HttpStatus.CONFLICT, "ROUTINE_ARCHIVED", "Routine is archived");
    claim(id, e);
  }

  private void claim(String id, long e) {
    if (jdbc.sql(
                "update routine.routines set version=version+1,updated_at=now() where id=cast(:id"
                    + " as uuid) and version=:v and archived_at is null")
            .param("id", id)
            .param("v", e)
            .update()
        != 1)
      throw new ApiException(
          HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED", "If-Match does not match");
  }

  private int count(String id) {
    return jdbc.sql(
            "select count(*) from routine.routine_sessions where routine_id=cast(:id as uuid)")
        .param("id", id)
        .query(Integer.class)
        .single();
  }

  private RoutineResponse aggregate(String id, boolean sessions) {
    var r =
        jdbc.sql("select * from routine.routines where id=cast(:id as uuid)")
            .param("id", id)
            .query(
                (rs, i) ->
                    new RoutineResponse(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("notes"),
                        instant(rs, "archived_at"),
                        rs.getLong("version"),
                        instant(rs, "created_at"),
                        instant(rs, "updated_at"),
                        List.<SessionResponse>of()))
            .single();
    if (!sessions) return r;
    var ids =
        jdbc.sql(
                "select id from routine.routine_sessions where routine_id=cast(:id as uuid) order"
                    + " by position")
            .param("id", id)
            .query(String.class)
            .list();
    var ss = ids.stream().map(this::session).toList();
    return new RoutineResponse(
        r.id(), r.name(), r.notes(), r.archivedAt(), r.version(), r.createdAt(), r.updatedAt(), ss);
  }

  private SessionResponse session(String id) {
    var s =
        jdbc.sql("select id,name,position from routine.routine_sessions where id=cast(:id as uuid)")
            .param("id", id)
            .query(
                (rs, i) ->
                    new SessionResponse(
                        rs.getString("id"), rs.getString("name"), rs.getInt("position"), List.of()))
            .single();
    var e =
        jdbc.sql(
                "select exercise_name,position from routine.routine_session_exercises where"
                    + " session_id=cast(:id as uuid) order by position")
            .param("id", id)
            .query(
                (rs, i) ->
                    new ExerciseResponse(rs.getString("exercise_name"), rs.getInt("position")))
            .list();
    return new SessionResponse(s.id(), s.name(), s.position(), e);
  }

  private Row sessionRow(String rid, String sid) {
    return jdbc.sql(
            "select id,position from routine.routine_sessions where id=cast(:sid as uuid) and"
                + " routine_id=cast(:rid as uuid)")
        .param("sid", sid)
        .param("rid", rid)
        .query((rs, i) -> new Row(rs.getString("id"), rs.getInt("position")))
        .optional()
        .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "Session was not found"));
  }

  private void routine(String id) {
    if (jdbc.sql("select count(*) from routine.routines where id=cast(:id as uuid)")
            .param("id", id)
            .query(Integer.class)
            .single()
        == 0) throw ApiException.notFound("ROUTINE_NOT_FOUND", "Routine was not found");
  }

  private static String text(String s, String f, int max) {
    if (s == null || s.trim().isEmpty()) throw ApiException.validation("value is required", f);
    String x = s.trim();
    if (x.length() > max) throw ApiException.validation("value is too long", f);
    return x;
  }

  private static long ifMatch(String e) {
    if (e == null || e.isBlank())
      throw new ApiException(
          HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", "If-Match is required");
    try {
      return Long.parseLong(e.replace("\"", ""));
    } catch (Exception x) {
      throw new ApiException(
          HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED", "If-Match does not match");
    }
  }

  private static void checkLimit(int l) {
    if (l < 1 || l > 100) throw ApiException.validation("limit is invalid", "limit");
  }

  private static Instant instant(java.sql.ResultSet r, String c) throws java.sql.SQLException {
    var x = r.getObject(c, java.time.OffsetDateTime.class);
    return x == null ? null : x.toInstant();
  }

  private static String encode(Cursor c) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((c.archived() + "|" + c.updated() + "|" + c.id()).getBytes());
  }

  private static Cursor decode(String x) {
    if (x == null) return null;
    try {
      String[] p = new String(Base64.getUrlDecoder().decode(x)).split("\\|", 3);
      return new Cursor(Boolean.parseBoolean(p[0]), Instant.parse(p[1]), p[2]);
    } catch (Exception e) {
      throw ApiException.badRequest("INVALID_CURSOR", "Cursor is invalid");
    }
  }

  private static String cursor(String rid, String sid, int limit, long version, int offset) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            (rid + "|" + sid + "|" + limit + "|" + version + "|" + offset)
                .getBytes(StandardCharsets.UTF_8));
  }

  private static int recordOffset(String c, String rid, String sid, int limit, long version) {
    if (c == null) return 0;
    try {
      String[] p =
          new String(Base64.getUrlDecoder().decode(c), StandardCharsets.UTF_8).split("\\|", 5);
      if (!p[0].equals(rid)
          || !p[1].equals(sid)
          || Integer.parseInt(p[2]) != limit
          || Long.parseLong(p[3]) != version) throw new Exception();
      int i = Integer.parseInt(p[4]);
      if (i < 0) throw new Exception();
      return i;
    } catch (Exception e) {
      throw ApiException.badRequest("INVALID_CURSOR", "Cursor is invalid");
    }
  }

  private static ApiException downstream(HttpHeaders h) {
    return new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "EXERCISE_SERVICE_UNAVAILABLE",
        "Exercise service unavailable",
        null,
        h.getFirst("Retry-After"));
  }

  private record Cursor(boolean archived, Instant updated, String id) {}

  private record Row(String id, int position) {}
}
