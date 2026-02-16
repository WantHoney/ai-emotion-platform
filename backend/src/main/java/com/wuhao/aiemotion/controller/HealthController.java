package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.integration.ser.SerClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final SerClient serClient;

    public HealthController(JdbcTemplate jdbcTemplate, SerClient serClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.serClient = serClient;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        boolean db = dbOk();
        boolean ser = serClient.probeHealth();
        return Map.of(
                "status", (db && ser) ? "UP" : "DEGRADED",
                "db", db ? "UP" : "DOWN",
                "ser", ser ? "UP" : "DOWN"
        );
    }

    @GetMapping("/api/health/db")
    public Map<String, Object> healthDb() {
        return Map.of("status", dbOk() ? "UP" : "DOWN");
    }

    @GetMapping("/api/health/ser")
    public Map<String, Object> healthSer() {
        return Map.of("status", serClient.probeHealth() ? "UP" : "DOWN");
    }

    private boolean dbOk() {
        try {
            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception e) {
            return false;
        }
    }
}
