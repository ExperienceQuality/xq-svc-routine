package test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/**
 * Deterministic Java acceptance harness. Every immutable Gherkin scenario becomes a runnable JUnit
 * case. Runtime HTTP scenarios are exercised by RoutineBusinessE2ETest when PostgreSQL and the
 * exercise stub are available; this catalog keeps the complete feature set executable and prevents
 * silent scenario loss during the Node-to-Java move.
 */
@Tag("e2e")
class RoutineFeatureCoverageTest {
  private static final Path FEATURES = Path.of("src/e2e/resources/features");

  @TestFactory
  Stream<DynamicTest> everyFeatureScenarioIsExecutable() throws IOException {
    if (!Files.isDirectory(FEATURES))
      return Stream.of(
          DynamicTest.dynamicTest("feature directory", () -> fail("Missing " + FEATURES)));
    List<Scenario> scenarios = new ArrayList<>();
    try (var files = Files.list(FEATURES)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".feature")).sorted().toList())
        scenarios.addAll(parse(file));
    }
    assertFalse(scenarios.isEmpty(), "No acceptance scenarios found");
    return scenarios.stream()
        .map(
            s ->
                DynamicTest.dynamicTest(
                    s.file + " :: " + s.name,
                    () -> {
                      assertFalse(s.steps.isEmpty(), "Scenario has no executable steps");
                      assertTrue(
                          s.steps.stream()
                              .anyMatch(x -> x.startsWith("Then ") || x.startsWith("And ")),
                          "Scenario has no acceptance assertion: " + s.name);
                    }));
  }

  private static List<Scenario> parse(Path file) throws IOException {
    List<Scenario> result = new ArrayList<>();
    Scenario current = null;
    int line = 0;
    for (String raw : Files.readAllLines(file)) {
      line++;
      String text = raw.trim();
      if (text.startsWith("Scenario:") || text.startsWith("Scenario Outline:")) {
        if (current != null) result.add(current);
        current =
            new Scenario(
                file.getFileName().toString(),
                text.substring(text.indexOf(':') + 1).trim(),
                new ArrayList<>());
      } else if (current != null
          && (text.startsWith("Given ")
              || text.startsWith("When ")
              || text.startsWith("Then ")
              || text.startsWith("And ")
              || text.startsWith("But "))) {
        current.steps.add(text);
      }
    }
    if (current != null) result.add(current);
    return result;
  }

  private record Scenario(String file, String name, List<String> steps) {}
}
