package com.xq.routineservice.routine;

import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
public final class HealthController {
  private final JdbcClient jdbc;

  public HealthController(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/livez")
  Map<String, String> live() {
    return Map.of("status", "UP");
  }

  @GetMapping("/readyz")
  Map<String, String> ready() {
    jdbc.sql("select 1").query(Integer.class).single();
    return Map.of("status", "UP");
  }
}
