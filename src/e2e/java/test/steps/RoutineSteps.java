package test.steps;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xq.jvmtestkit.cucumber.XqCucumberContext;
import com.xq.jvmtestkit.cucumber.XqCucumberHooks;
import com.xq.jvmtestkit.rest.RestRequest;
import com.xq.jvmtestkit.rest.RestResponse;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.*;
import io.cucumber.java.en.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/** Explicit acceptance glue. Missing feature steps remain undefined and fail Cucumber. */
public final class RoutineSteps {
  private static final String ROOT = "/api/v1/routines";
  private static final ObjectMapper JSON = new ObjectMapper();
  private final HttpClient http = HttpClient.newHttpClient();
  private final XqCucumberContext context;
  private final XqCucumberHooks hooks;
  private RestResponse response;
  private String routineId, sessionId, etag, previousEtag, staleEtag, nextCursor;
  private List<String> firstIds = List.of();

  public RoutineSteps(XqCucumberContext context) {
    this.context = context;
    this.hooks = new XqCucumberHooks(context);
  }

  @Before(order = Integer.MIN_VALUE)
  public void before(Scenario s) {
    hooks.beforeScenario(s);
  }

  @After(order = Integer.MAX_VALUE)
  public void after(Scenario s) {
    hooks.afterScenario(s);
  }

  @When("I request service liveness")
  public void live() {
    response = context.rest().get("/livez");
  }

  @When("I request service readiness")
  public void ready() {
    response = context.rest().get("/readyz");
  }

  @Then("the health status is {string}")
  public void health(String v) {
    assertEquals(v, body().path("status").asText());
  }

  @When("I create a routine named {string} with notes {string}")
  public void createNotes(String n, String x) {
    create(Map.of("name", n, "notes", x));
  }

  @When("I create a routine named {string} without notes")
  public void createNoNotes(String n) {
    create(Map.of("name", n));
  }

  @Given("I created a routine named {string}")
  public void created(String n) {
    create(Map.of("name", n));
    assertEquals(201, response.statusCode());
  }

  @Given("I created a routine named {string} and notes {string}")
  public void createdNotes(String n, String x) {
    create(Map.of("name", n, "notes", x));
    assertEquals(201, response.statusCode());
  }

  @When("I submit a routine creation with name {string}")
  public void invalidCreate(String n) {
    create(Map.of("name", n));
  }

  @When("I submit a routine creation containing an unknown property")
  public void unknownProperty() {
    create(Map.of("name", "Unexpected field", "color", "blue"));
  }

  @When("I submit malformed JSON to create a routine")
  public void malformedCreate() {
    response = raw("POST", ROOT, "{\"name\":", Map.of("content-type", "application/json"));
  }

  @When("I retrieve the current routine")
  public void retrieve() {
    response = context.rest().get(path());
  }

  @When("I retrieve an unknown routine")
  public void unknownRoutine() {
    response = context.rest().get(ROOT + "/" + UUID.randomUUID());
  }

  @When("I list active routines with limit {int}")
  public void active(int n) {
    list(n, false);
  }

  @When("I list routines including archived routines with limit {int}")
  public void all(int n) {
    list(n, true);
  }

  @When("I list routines with invalid cursor {string}")
  public void badCursor(String c) {
    response = context.rest().get(ROOT + "?limit=20&includeArchived=false&cursor=" + c);
  }

  @Given("I created {int} active routines for pagination")
  public void createdMany(int n) {
    for (int i = 0; i < n; i++) create(Map.of("name", "Pagination " + context.runId() + " " + i));
  }

  @Given("I listed active routines with limit {int} and saved the next cursor")
  public void savedCursor(int n) {
    list(n, false);
    nextCursor = body().path("nextCursor").asText();
  }

  @When("I use the saved cursor while including archived routines")
  public void changedFilter() {
    response = context.rest().get(ROOT + "?limit=20&includeArchived=true&cursor=" + nextCursor);
  }

  @When("I follow the routine list next cursor with limit {int}")
  public void followList(int n) {
    response =
        context.rest().get(ROOT + "?limit=" + n + "&includeArchived=false&cursor=" + nextCursor);
  }

  @When("I update the routine name to {string} and notes to {string} with the saved ETag")
  public void patchBoth(String n, String x) {
    patch(Map.of("name", n, "notes", x), etag);
  }

  @When("I update the routine name to {string} with the saved ETag")
  public void patchName(String n) {
    patch(Map.of("name", n), etag);
  }

  @When("I update the routine name to {string} with the stale ETag")
  public void patchStale(String n) {
    patch(Map.of("name", n), staleEtag);
  }

  @When("I clear the routine notes with the saved ETag")
  public void clearNotes() {
    var p = new HashMap<String, Object>();
    p.put("notes", null);
    patch(p, etag);
  }

  @When("I update the routine without If-Match")
  public void noMatch() {
    response =
        raw(
            "PATCH",
            path(),
            "{\"name\":\"Must not update\"}",
            Map.of("content-type", "application/merge-patch+json"));
  }

  @Given("I saved the current ETag as stale")
  public void saveStale() {
    staleEtag = etag;
  }

  @When("I archive the current routine with the saved ETag")
  public void archive() {
    response = raw("DELETE", path(), "", Map.of("if-match", String.valueOf(etag)));
  }

  @Given("I archived the current routine")
  public void archivedGiven() {
    archive();
    assertEquals(204, response.statusCode());
  }

  @When("I archive the current routine without If-Match")
  public void archiveNoMatch() {
    response = raw("DELETE", path(), "", Map.of());
  }

  @When("I replace the current session with name {string} at position {int} and exercises:")
  public void replaceCurrent(String n, int p, DataTable t) {
    replace(sessionId, n, p, names(t), etag);
  }

  @When("I replace session {string} with name {string} at position {int} and exercises {string}")
  public void replaceNamed(String old, String n, int p, String xs) {
    replace(find(old).path("id").asText(), n, p, csv(xs), etag);
  }

  @When("I replace an unknown session with the saved ETag")
  public void replaceUnknown() {
    replace(UUID.randomUUID().toString(), "Unknown", 0, List.of("Squat"), etag);
  }

  @When("I replace the current session without If-Match")
  public void replaceNoMatch() {
    replace(sessionId, "Must not replace", 0, List.of("Squat"), null);
  }

  @When("I replace session {string} with the stale ETag")
  public void replaceStale(String n) {
    JsonNode s = find(n);
    replace(
        s.path("id").asText(),
        n,
        s.path("position").asInt(),
        s.path("exercises").findValuesAsText("name"),
        staleEtag);
  }

  @When("I delete the current session without If-Match")
  public void deleteNoMatch() {
    response = raw("DELETE", path() + "/sessions/" + sessionId, "", Map.of());
  }

  @When("I delete session {string} with the stale ETag")
  public void deleteStale(String n) {
    response =
        raw(
            "DELETE",
            path() + "/sessions/" + find(n).path("id").asText(),
            "",
            Map.of("if-match", String.valueOf(staleEtag)));
  }

  @When("I delete session {string} with the saved ETag")
  public void deleteSaved(String n) {
    response =
        raw(
            "DELETE",
            path() + "/sessions/" + find(n).path("id").asText(),
            "",
            Map.of("if-match", String.valueOf(etag)));
  }

  @Given("the routine already has 50 sessions")
  public void fiftySessions() {
    for (int i = 0; i < 50; i++) add("Session " + i, i, List.of("Exercise " + i), etag);
  }

  @Then("the response status is {int}")
  public void status(int n) {
    assertEquals(n, response.statusCode(), response.bodyUtf8());
  }

  @Then("the response has a Location header for the routine")
  public void location() {
    assertEquals(path(), header("location"));
  }

  @Then("the response has a strong ETag")
  public void strong() {
    etag = header("etag");
    assertTrue(etag != null && etag.startsWith("\""), String.valueOf(etag));
  }

  @Then("the response has a newer strong ETag")
  public void newer() {
    String n = header("etag");
    assertTrue(n != null && n.startsWith("\""));
    assertNotEquals(previousEtag == null ? etag : previousEtag, n);
    etag = n;
    previousEtag = null;
  }

  @Then("the response ETag equals the saved ETag")
  public void same() {
    assertEquals(etag, header("etag"));
  }

  @Then("the routine response has name {string} and notes {string}")
  public void routine(String n, String x) {
    assertEquals(n.trim(), body().path("name").asText());
    assertEquals(x.trim(), body().path("notes").asText());
  }

  @Then("the routine response has name {string} and null notes")
  public void nullNotes(String n) {
    assertEquals(n.trim(), body().path("name").asText());
    assertTrue(body().path("notes").isNull());
  }

  @Then("the routine response has no sessions")
  public void noSessions() {
    assertTrue(body().path("sessions").isArray() && body().path("sessions").isEmpty());
  }

  @Then("the routine response is archived")
  public void isArchived() {
    assertFalse(body().path("archivedAt").isNull());
  }

  @Then("the routine list contains the current routine")
  public void contains() {
    assertTrue(items().findValuesAsText("id").contains(routineId));
  }

  @Then("the routine list does not contain the current routine")
  public void excludes() {
    assertFalse(items().findValuesAsText("id").contains(routineId));
  }

  @Then("the routine list does not expose sessions")
  public void noListSessions() {
    for (JsonNode n : items()) assertFalse(n.has("sessions"));
  }

  @Then("the problem code is {string}")
  public void problem(String c) {
    assertEquals(c, body().path("code").asText());
  }

  @Then("the problem identifies field {string}")
  public void field(String f) {
    boolean ok = false;
    for (JsonNode n : body().path("violations")) ok |= f.equals(n.path("field").asText());
    assertTrue(ok, body().toString());
  }

  @When("I add session {string} at position {int} with exercises:")
  public void addTable(String n, int p, DataTable t) {
    add(n, p, names(t), etag);
  }

  @Given("I added session {string} at position {int} with exercises {string}")
  public void added(String n, int p, String x) {
    add(n, p, csv(x), etag);
    assertEquals(201, response.statusCode());
  }

  @When("I add session {string} with the saved ETag")
  public void addSaved(String n) {
    add(n, 0, List.of("Squat"), etag);
  }

  @When("I add session {string} with the stale ETag")
  public void addStale(String n) {
    add(n, 0, List.of("Squat"), staleEtag);
  }

  @When("I add a session without If-Match")
  public void addNoMatch() {
    add(none(), 0, List.of("Squat"), null);
  }

  @When("I add a session named {string}")
  public void addNamed(String n) {
    add(n, 0, List.of("Squat"), etag);
  }

  @When("I add a session containing 51 exercise names")
  public void addTooMany() {
    add(
        "Too many",
        0,
        java.util.stream.IntStream.range(1, 52).mapToObj(i -> "Exercise " + i).toList(),
        etag);
  }

  @When("I add a session to an unknown routine")
  public void addUnknown() {
    response =
        context
            .rest()
            .post(
                ROOT + "/" + UUID.randomUUID() + "/sessions",
                RestRequest.builder()
                    .header("if-match", "\"1\"")
                    .jsonBody(
                        Map.of(
                            "name",
                            "Unknown",
                            "position",
                            0,
                            "exercises",
                            List.of(Map.of("name", "Squat"))))
                    .build());
  }

  @Then("session {string} is at position {int}")
  public void sessionPosition(String n, int p) {
    assertEquals(p, find(n).path("position").asInt());
  }

  @Then("session {string} has exercises in order:")
  public void sessionExercises(String n, DataTable t) {
    assertEquals(names(t), find(n).path("exercises").findValuesAsText("name"));
  }

  @Then("the routine has sessions in order {string}")
  public void sessionOrder(String x) {
    response = context.rest().get(path());
    assertEquals(csv(x), directNames(body().path("sessions"), "name"));
  }

  @Then("session positions are contiguous from zero")
  public void positions() {
    response = context.rest().get(path());
    for (int i = 0; i < body().path("sessions").size(); i++)
      assertEquals(i, body().path("sessions").get(i).path("position").asInt());
  }

  @Then("the routine contains no session named {string}")
  public void noSession(String n) {
    response = context.rest().get(path());
    assertFalse(body().path("sessions").findValuesAsText("name").contains(n));
  }

  @Given("the exercise service returns no logs for {string}")
  public void emptyLogs(String n) {
    stub(n, "[]", 200, Map.of());
  }

  @Given("the exercise service returns these logs for {string}:")
  public void tabularLogs(String n, DataTable table) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map<String, String> r : table.asMaps()) {
      var x = new HashMap<String, Object>();
      x.put("id", Integer.parseInt(r.get("id")));
      x.put("exerciseName", r.get("exerciseName"));
      x.put("loggedAt", r.get("loggedAt"));
      x.put("sets", List.of());
      x.put("highestSetVolumeKg", Integer.parseInt(r.get("highestSetVolumeKg")));
      rows.add(x);
    }
    stub(n, json(Map.of("items", rows)), 200, Map.of());
  }

  @Given("the exercise service returns malformed data for {string}")
  public void badLogs(String n) {
    stub(n, "{\"logs\":\"not-an-array\"}", 200, Map.of());
  }

  @Given("the exercise service is unavailable for {string}")
  public void unavailable(String n) {
    stub(n, "{\"code\":\"SERVICE_UNAVAILABLE\"}", 503, Map.of());
  }

  @Given("the exercise service rate limits {string} for {int} seconds")
  public void limited(String n, int s) {
    stub(n, "{\"code\":\"RATE_LIMITED\"}", 429, Map.of("Retry-After", String.valueOf(s)));
  }

  @Given("the exercise service is unavailable for every request")
  public void unavailableAll() {
    stub("*", "{\"code\":\"SERVICE_UNAVAILABLE\"}", 503, Map.of());
  }

  @When("I retrieve exercise records for the current session with limit {int}")
  public void records(int n) {
    response =
        context.rest().get(path() + "/exercise-records?sessionId=" + sessionId + "&limit=" + n);
  }

  @When("I retrieve exercise records without a session ID")
  public void recordsNoSession() {
    response = context.rest().get(path() + "/exercise-records?limit=20");
  }

  @When("I retrieve exercise records for an unknown session")
  public void recordsUnknown() {
    response =
        context
            .rest()
            .get(path() + "/exercise-records?sessionId=" + UUID.randomUUID() + "&limit=20");
  }

  @When("I retrieve exercise records with invalid cursor {string}")
  public void recordsBadCursor(String c) {
    response =
        context
            .rest()
            .get(path() + "/exercise-records?sessionId=" + sessionId + "&limit=20&cursor=" + c);
  }

  @Then("the response Retry-After header is {string}")
  public void retry(String s) {
    assertEquals(s, header("retry-after"));
  }

  @Then("the routine list has {int} items")
  public void listCount(int n) {
    assertEquals(n, items().size());
  }

  @Then("the routine list has a next cursor")
  public void listNext() {
    nextCursor = body().path("nextCursor").asText();
    assertFalse(nextCursor.isBlank());
    firstIds = items().findValuesAsText("id");
  }

  @Then("the two routine pages have no duplicate IDs")
  public void noRoutineDuplicates() {
    for (String id : items().findValuesAsText("id")) assertFalse(firstIds.contains(id));
  }

  @Then("the composed records have exercise names in order {string}")
  public void recordNames(String x) {
    assertEquals(csv(x), items().findValuesAsText("exerciseName"));
  }

  @Then("the composed record collection is empty")
  public void recordsEmpty() {
    assertEquals(0, items().size());
  }

  @Then("the composed record list has {int} items")
  public void recordCount(int n) {
    assertEquals(n, items().size());
  }

  @Then("the composed record list has a next cursor")
  public void recordNext() {
    nextCursor = body().path("nextCursor").asText();
    assertFalse(nextCursor.isBlank());
    firstIds = items().findValuesAsText("id");
  }

  @When("I follow the composed record next cursor with limit {int}")
  public void followRecords(int n) {
    response =
        context
            .rest()
            .get(
                path()
                    + "/exercise-records?sessionId="
                    + sessionId
                    + "&limit="
                    + n
                    + "&cursor="
                    + nextCursor);
  }

  @Then("the two composed record pages have no duplicate IDs")
  public void noRecordDuplicates() {
    for (String id : items().findValuesAsText("id")) assertFalse(firstIds.contains(id));
  }

  @Then("the composed response contains the complete detailed log for {string}")
  public void detailed(String n) {
    assertEquals(n, items().get(0).path("exerciseName").asText());
    assertTrue(items().get(0).path("sets").isArray());
  }

  @Then("the exercise service was not called")
  public void notCalled() {
    context.stub().verify(0, getRequestedFor(urlPathEqualTo("/api/v1/exercise-logs")));
  }

  @Then("the exercise service received no mutation request")
  public void noMutation() {
    context.stub().verify(0, postRequestedFor(urlPathEqualTo("/api/v1/exercise-logs")));
    context.stub().verify(0, putRequestedFor(urlPathEqualTo("/api/v1/exercise-logs")));
    context.stub().verify(0, patchRequestedFor(urlPathEqualTo("/api/v1/exercise-logs")));
    context.stub().verify(0, deleteRequestedFor(urlPathEqualTo("/api/v1/exercise-logs")));
  }

  @Then("the exercise service received one GET for {string}")
  public void oneGet(String n) {
    context
        .stub()
        .verify(
            1,
            getRequestedFor(urlPathEqualTo("/api/v1/exercise-logs"))
                .withQueryParam("exerciseName", equalTo(n))
                .withQueryParam("limit", equalTo("100")));
  }

  @Given("the exercise service returns a detailed log for {string}")
  public void detailedStub(String n) {
    stub(
        n,
        "{\"items\":[{\"id\":41,\"exerciseName\":\""
            + n
            + "\",\"loggedAt\":\"2026-09-04T08:00:00.000Z\",\"sets\":[{\"id\":401,\"setNumber\":1,\"weightKg\":100,\"reps\":5,\"volumeKg\":500}],\"highestSetVolumeKg\":500}]}",
        200,
        Map.of());
    stub("Bench Press", "{\"items\":[]}", 200, Map.of());
  }

  private void create(Object x) {
    response = context.rest().post(ROOT, RestRequest.builder().jsonBody(x).build());
    if (response.statusCode() == 201) {
      routineId = body().path("id").asText();
      etag = header("etag");
    }
  }

  private void list(int n, boolean a) {
    response = context.rest().get(ROOT + "?limit=" + n + "&includeArchived=" + a);
  }

  private void patch(Object x, String e) {
    response =
        raw(
            "PATCH",
            path(),
            json(x),
            Map.of("content-type", "application/merge-patch+json", "if-match", String.valueOf(e)));
    if (response.statusCode() == 200) {
      previousEtag = etag;
      etag = header("etag");
    }
  }

  private void add(String n, int p, List<String> es, String e) {
    List<Map<String, Object>> xs = new ArrayList<>();
    for (String exercise : es) {
      var x = new HashMap<String, Object>();
      x.put("name", exercise);
      xs.add(x);
    }
    var request = new HashMap<String, Object>();
    request.put("name", n);
    request.put("position", p);
    request.put("exercises", xs);
    var b = RestRequest.builder().jsonBody(request);
    if (e != null) b.header("if-match", e);
    response = context.rest().post(path() + "/sessions", b.build());
    if (response.statusCode() == 201) {
      sessionId = body().path("id").asText();
      previousEtag = etag;
      etag = header("etag");
    }
  }

  private void replace(String id, String n, int p, List<String> es, String e) {
    List<Map<String, Object>> xs = new ArrayList<>();
    for (String exercise : es) {
      var x = new HashMap<String, Object>();
      x.put("name", exercise);
      xs.add(x);
    }
    var request = new HashMap<String, Object>();
    request.put("name", n);
    request.put("position", p);
    request.put("exercises", xs);
    var headers = new HashMap<String, String>();
    headers.put("content-type", "application/json");
    if (e != null) headers.put("if-match", e);
    response = raw("PUT", path() + "/sessions/" + id, json(request), headers);
    if (response.statusCode() == 200) {
      sessionId = id;
      etag = header("etag");
    }
  }

  private JsonNode body() {
    try {
      return JSON.readTree(response.bodyUtf8());
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private JsonNode items() {
    return body().path("items");
  }

  private JsonNode find(String n) {
    response = context.rest().get(path());
    for (JsonNode x : body().path("sessions")) if (n.equals(x.path("name").asText())) return x;
    throw new AssertionError("session not found: " + n);
  }

  private String path() {
    return ROOT + "/" + Objects.requireNonNull(routineId, "routineId");
  }

  private String header(String n) {
    return response.headers().entrySet().stream()
        .filter(e -> e.getKey().equalsIgnoreCase(n))
        .findFirst()
        .map(e -> e.getValue().get(0))
        .orElse(null);
  }

  private List<String> names(DataTable t) {
    return t.asMaps().stream()
        .map(
            r -> {
              String v = r.get("exerciseName");
              if (v == null && r.size() == 1) v = r.values().iterator().next();
              return v == null ? "" : v.trim();
            })
        .toList();
  }

  private List<String> directNames(JsonNode a, String key) {
    List<String> out = new ArrayList<>();
    for (JsonNode n : a) out.add(n.path(key).asText());
    return out;
  }

  private List<String> csv(String x) {
    return Arrays.stream(x.split(",")).map(String::trim).toList();
  }

  private String none() {
    return "No precondition";
  }

  private String json(Object x) {
    try {
      return JSON.writeValueAsString(x);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private RestResponse raw(String method, String uri, String payload, Map<String, String> headers) {
    try {
      var b =
          HttpRequest.newBuilder(
                  URI.create(
                      System.getenv().getOrDefault("XQORB_BASE_URI", "http://127.0.0.1:18090")
                          + uri))
              .timeout(Duration.ofSeconds(10))
              .method(method, HttpRequest.BodyPublishers.ofString(payload));
      headers.forEach(b::header);
      var r = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
      return new RestResponse(r.statusCode(), r.headers().map(), r.body());
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private void stub(String exercise, String payload, int status, Map<String, String> hs) {
    var q = exercise.equals("*") ? matching(".*") : equalTo(exercise);
    var a =
        aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(payload);
    hs.forEach(a::withHeader);
    context
        .stub()
        .stubFor(
            get(urlPathEqualTo("/api/v1/exercise-logs"))
                .withQueryParam("exerciseName", q)
                .willReturn(a));
  }
}
