package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class TokenInvalidException extends ApiException{
    public TokenInvalidException() {
        super("TOKEN_INVALID",
                "Verification token is invalid",
                HttpStatus.BAD_REQUEST,
                new FieldDetail("Email", "invalid token"));
    }
}
