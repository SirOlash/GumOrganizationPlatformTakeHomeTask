package com.Semicolon.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthResponse {
    private String status;
    private Map<String, String> checks;
    private Map<String, Object> details;
}
