package com.Semicolon.api;

import lombok.Data;

import java.util.List;

@Data
public class ErrorPayload {
    private String code;
    private String message;
    private List<FieldDetail> details;
    private String trace_id;

    public ErrorPayload(String code, String message, List<FieldDetail> details, String trace_id) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.trace_id = trace_id;
    }
}
