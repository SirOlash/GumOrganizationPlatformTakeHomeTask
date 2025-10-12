package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class InvalidLogoException extends ApiException{
    public InvalidLogoException() {
        super("INVALID_LOGO",
                "The logo should be either png, jpeg, webp and below 2mb",
                HttpStatus.UNPROCESSABLE_ENTITY,
                new FieldDetail("Logo", "invalid_image"));
    }

    public InvalidLogoException(String message) {
        super("INVALID_LOGO",
                message,
                HttpStatus.UNPROCESSABLE_ENTITY,
                new FieldDetail("Logo", "invalid_image"));
    }

    public InvalidLogoException(String message, Exception e) {
        super("INVALID_LOGO",
                message,
                HttpStatus.UNPROCESSABLE_ENTITY,
                new FieldDetail("Logo", "invalid_image"));
    }
}
