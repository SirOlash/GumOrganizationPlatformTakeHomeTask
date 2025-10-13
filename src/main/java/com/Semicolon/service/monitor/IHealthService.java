package com.Semicolon.service.monitor;


import com.Semicolon.dtos.response.HealthResponse;

public interface IHealthService {
    HealthResponse checkHealth();
    boolean isDbUp();
}
