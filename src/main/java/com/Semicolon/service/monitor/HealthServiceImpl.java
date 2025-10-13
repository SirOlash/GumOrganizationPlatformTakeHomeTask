package com.Semicolon.service.monitor;

import com.Semicolon.dtos.response.HealthResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

@Service
public class HealthServiceImpl implements IHealthService {

    private final JdbcTemplate jdbc;

    public HealthServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public HealthResponse checkHealth() {
        boolean dbUp = isDbUp();

        Map<String, String> checks = new HashMap<>();
        checks.put("db", dbUp ? "ok" : "not_ok");

        String status = dbUp ? "ok" : "degraded";

        Map<String, Object> details = new HashMap<>();
        details.put("appUptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        return new HealthResponse(status, checks, details);
    }

    @Override
    public boolean isDbUp() {
        try {
            Integer v = jdbc.queryForObject("SELECT 1", Integer.class);
            return v != null;
        } catch (Exception ex) {
            return false;
        }
    }
}
