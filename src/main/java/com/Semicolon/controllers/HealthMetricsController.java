package com.Semicolon.controllers;

import com.Semicolon.dtos.response.HealthResponse;
import com.Semicolon.service.monitor.IHealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

@RestController
public class HealthMetricsController {

    private final IHealthService healthService;

    public HealthMetricsController(IHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping(path = "/.well-known/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse resp = healthService.checkHealth();
        if ("ok".equalsIgnoreCase(resp.getStatus())) {
            return ResponseEntity.ok(resp);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(resp);
        }
    }

    @GetMapping(path = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        StringBuilder sb = new StringBuilder();

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        double uptimeSec = uptimeMs / 1000.0;
        sb.append("# HELP app_uptime_seconds Application uptime in seconds\n");
        sb.append("# TYPE app_uptime_seconds gauge\n");
        sb.append("app_uptime_seconds ").append(String.format("%.3f", uptimeSec)).append("\n\n");

        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        long used = heap.getUsed();
        sb.append("# HELP jvm_memory_used_bytes JVM heap used bytes\n");
        sb.append("# TYPE jvm_memory_used_bytes gauge\n");
        sb.append("jvm_memory_used_bytes ").append(used).append("\n\n");

        boolean dbUp = healthService.isDbUp();
        sb.append("# HELP db_up Database connectivity (1 = up, 0 = down)\n");
        sb.append("# TYPE db_up gauge\n");
        sb.append("db_up ").append(dbUp ? "1" : "0").append("\n");

        return sb.toString();
    }
}
