package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class ProgramConflictException extends ApiException {
    public ProgramConflictException(String message) {
        super("PROGRAM_CONFLICT",
                message,
                HttpStatus.CONFLICT,
                new FieldDetail("Program", "conflict"));
    }
}
