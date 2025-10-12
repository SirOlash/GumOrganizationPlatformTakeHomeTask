package com.Semicolon.exceptions.api;


import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException{
    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS",
                "That didn't work. Check your details and try again",
                HttpStatus.UNAUTHORIZED,
                new FieldDetail("Email", "invalid_credentials"));
    }
}



