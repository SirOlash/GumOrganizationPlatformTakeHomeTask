package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class RateLimitedException extends ApiException{
    public RateLimitedException() {
        super("RATE_LIMITED",
                "Too many login attempts. Try again later.",
                HttpStatus.TOO_MANY_REQUESTS,
                new FieldDetail("Email", "too many attempts")
        );
    }
}

