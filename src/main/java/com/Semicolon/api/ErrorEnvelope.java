package com.Semicolon.api;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
public class ErrorEnvelope {
    private ErrorPayload error;
}

