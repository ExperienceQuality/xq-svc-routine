package com.xq.exerciseservice;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
class ExerciseApiIntegrationTests {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18.6-bookworm"));

    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.sql("truncate exercise_sets, exercise_logs restart identity cascade").update();
    }

    @Test
    void createsLogAndCalculatesHighestSingleSetVolume() throws Exception {
        mvc.perform(post("/api/v1/exercise-logs")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"exerciseName":"Bench Press","sets":[
                                  {"setNumber":1,"weightKg":60.00,"reps":10},
                                  {"setNumber":2,"weightKg":65.00,"reps":8}
                                ]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exerciseName").value("Bench Press"))
                .andExpect(jsonPath("$.sets.length()").value(2))
                .andExpect(jsonPath("$.sets[0].volumeKg").value(600.00))
                .andExpect(jsonPath("$.highestSetVolumeKg").value(600.00));
    }

    @Test
    void listsTopLogsByHighestVolumeAndDistinctExercises() throws Exception {
        createLog("Bench Press", 60, 10);
        createLog("Bench Press", 70, 8);
        createLog("Bench Press", 50, 5);
        createLog("Squat", 100, 5);

        mvc.perform(get("/api/v1/exercise-logs")
                        .param("exerciseName", "bench press")
                        .param("sort", "highestSetVolume")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].highestSetVolumeKg").value(600.00))
                .andExpect(jsonPath("$[1].highestSetVolumeKg").value(560.00));

        mvc.perform(get("/api/v1/exercise-logs/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].exerciseName").value("Bench Press"))
                .andExpect(jsonPath("$[0].logCount").value(3))
                .andExpect(jsonPath("$[1].exerciseName").value("Squat"));
    }

    @Test
    void rejectsInvalidSets() throws Exception {
        mvc.perform(post("/api/v1/exercise-logs")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"exerciseName":"Squat","sets":[
                                  {"setNumber":1,"weightKg":-1,"reps":0}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private void createLog(String name, int weight, int reps) throws Exception {
        mvc.perform(post("/api/v1/exercise-logs")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"exerciseName":"%s","sets":[
                                  {"setNumber":1,"weightKg":%d,"reps":%d}
                                ]}
                                """.formatted(name, weight, reps)))
                .andExpect(status().isCreated());
    }
}
