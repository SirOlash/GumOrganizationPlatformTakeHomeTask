package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class TokenUsedException extends ApiException {
    public TokenUsedException() {
        super("TOKEN_ALREADY_USED",
                "Verification token has already been used, Re-register to get a new token",
                HttpStatus.BAD_REQUEST,
                new FieldDetail("Email", "already used token"));
    }
}
