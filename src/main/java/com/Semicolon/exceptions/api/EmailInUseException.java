package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class EmailInUseException extends ApiException{
    public EmailInUseException() {
        super("EMAIL_IN_USE",
                "Email already registered",
                HttpStatus.CONFLICT,
                new FieldDetail("Email","in use"));
    }

    public EmailInUseException(String message) {
        super("EMAIL_IN_USE",
                message,
                HttpStatus.CONFLICT,
                new FieldDetail("email", "in use"));
    }

}

