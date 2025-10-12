package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class TokenExpiredException extends ApiException {
    public TokenExpiredException() {
        super("TOKEN_EXPIRED",
                "Verification token has expired. Re-register to get a new token",
                HttpStatus.GONE,
                new FieldDetail("Email", "expired-token"));
    }
}
