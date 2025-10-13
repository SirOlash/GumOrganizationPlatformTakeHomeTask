package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class NameValidationException extends ApiException{
    public NameValidationException(String message){
        super("INVALID_CREDENTIALS",
                "That didn't work. Check your details and try again",
                HttpStatus.UNPROCESSABLE_ENTITY,
                new FieldDetail("Name", "Not found"));

    }
}
