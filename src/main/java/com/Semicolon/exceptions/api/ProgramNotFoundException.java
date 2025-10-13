package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class ProgramNotFoundException extends ApiException{
    public ProgramNotFoundException(String message) {
        super("PROGRAM_NOT_FOUND",
                message,
                HttpStatus.NOT_FOUND,
                new FieldDetail("Program", "not found"));

    }
}
