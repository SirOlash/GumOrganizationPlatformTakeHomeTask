package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException{
    public UserNotFoundException(String message) {
        super("USER_NOT_FOUND",
                message,
                HttpStatus.NOT_FOUND,
                new FieldDetail("User", "not found"));
    }

}
