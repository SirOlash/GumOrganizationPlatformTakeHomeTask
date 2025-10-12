package com.Semicolon.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminRegistrationResponse {
    private String message;
    private String token;
}
