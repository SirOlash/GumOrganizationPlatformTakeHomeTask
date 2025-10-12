package com.Semicolon.exceptions.api;


import com.Semicolon.api.FieldDetail;
import lombok.Data;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Objects;

@Getter
public class ApiException extends RuntimeException{
    private final String code;
    private final HttpStatus status;
    private final List<FieldDetail> details;

    public ApiException(String code, String message, HttpStatus status, List<FieldDetail> details) {
        super(message);
        this.code = Objects.requireNonNull(code, "code required");
        this.status = Objects.requireNonNull(status, "status required");
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ApiException(String code, String message, HttpStatus status, FieldDetail singleDetail) {
        this(code, message, status, singleDetail == null ? List.of() : List.of(singleDetail));
    }
}