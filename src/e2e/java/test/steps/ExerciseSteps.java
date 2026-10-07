package test.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.xq.jvmtestkit.cucumber.XqCucumberContext;
import com.xq.jvmtestkit.cucumber.XqJsonTable;
import com.xq.jvmtestkit.rest.RestRequest;
import com.xq.jvmtestkit.rest.RestResponse;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ExerciseSteps {
    private final XqCucumberContext test;
    private RestResponse response;
    private String lastCreatedId;
    private String sortingExerciseName;

    public ExerciseSteps(XqCucumberContext test) {
        this.test = test;
    }

    @When("I create an exercise log:")
    public void createExerciseLog(DataTable table) {
        response = test.rest().post("/api/v1/exercise-logs",
                RestRequest.builder().jsonBody(XqJsonTable.compose(table)).build());
        response.should().hasStatus(201);
        lastCreatedId = jsonString(response.bodyUtf8(), "id");
    }

    @Then("the exercise log is created for {string}")
    public void exerciseLogIsCreated(String exerciseName) {
        response.should().hasStatus(201)
                .hasJsonPathValue("$.exerciseName", exerciseName)
                .hasJsonPathValue("$.highestSetVolumeKg", 600.00);
        assertThat(response.bodyUtf8()).contains("\"sets\"");
    }

    @When("I request the created exercise log")
    public void requestCreatedExerciseLog() {
        response = test.rest().get("/api/v1/exercise-logs/" + lastCreatedId);
    }

    @Then("the exercise log response contains {string} with volume {double}")
    public void exerciseLogResponseContains(String exerciseName, double volume) {
        response.should().hasStatus(200)
                .hasJsonPathValue("$.exerciseName", exerciseName)
                .hasJsonPathValue("$.highestSetVolumeKg", volume);
    }

    @When("I request the distinct exercise names")
    public void requestDistinctExerciseNames() {
        response = test.rest().get("/api/v1/exercise-logs/exercises");
    }

    @Then("the distinct exercise names include {string}")
    public void distinctExerciseNamesInclude(String exerciseName) {
        response.should().hasStatus(200);
        assertThat(response.bodyUtf8()).contains("\"exerciseName\":\"%s\"".formatted(exerciseName));
    }

    @Given("I create three logs for a sorting exercise")
    public void createThreeLogsForSortingExercise() {
        sortingExerciseName = "Cucumber Squat " + UUID.randomUUID();
        createLog(sortingExerciseName, 60, 10);
        createLog(sortingExerciseName, 80, 8);
        createLog(sortingExerciseName, 100, 5);
    }

    @When("I request the top two logs for the sorting exercise")
    public void requestTopTwoLogsForSortingExercise() {
        String encodedExerciseName = URLEncoder.encode(sortingExerciseName, StandardCharsets.UTF_8);
        response = test.rest().get("/api/v1/exercise-logs?exerciseName=" + encodedExerciseName
                + "&sort=highestSetVolume&limit=2");
    }

    @Then("the sorting response orders volumes {double} then {double} and excludes {double}")
    public void sortingResponseOrdersVolumes(double first, double second, double excluded) {
        response.should().hasStatus(200);
        String body = response.bodyUtf8();
        assertThat(body.indexOf("\"highestSetVolumeKg\":%s".formatted(formatVolume(first))))
                .isLessThan(body.indexOf("\"highestSetVolumeKg\":%s".formatted(formatVolume(second))));
        assertThat(body).doesNotContain("\"highestSetVolumeKg\":%s".formatted(formatVolume(excluded)));
    }

    private void createLog(String exerciseName, int weight, int reps) {
        RestResponse created = test.rest().post("/api/v1/exercise-logs",
                RestRequest.builder().jsonBody(Map.of(
                        "exerciseName", exerciseName,
                        "sets", List.of(Map.of("setNumber", 1, "weightKg", weight, "reps", reps))))
                        .build());
        created.should().hasStatus(201);
    }

    private String formatVolume(double volume) {
        return "%.2f".formatted(volume);
    }

    private String jsonString(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) throw new AssertionError("Missing JSON field: " + field);
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf(',', valueStart);
        if (valueEnd < 0) valueEnd = json.indexOf('}', valueStart);
        if (valueEnd < 0) throw new AssertionError("Unterminated JSON field: " + field);
        return json.substring(valueStart, valueEnd).replace("\"", "").trim();
    }
}
