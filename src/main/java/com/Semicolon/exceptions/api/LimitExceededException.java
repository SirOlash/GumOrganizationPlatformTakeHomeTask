package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class LimitExceededException extends ApiException{
    public LimitExceededException(String message) {
        super("LIMIT_EXCEEDED",
                message,
                HttpStatus.TOO_MANY_REQUESTS,
                new FieldDetail("Limit", "Exceeded"));
    }

    public LimitExceededException(String message, String field, String rule) {
        super("LIMIT_EXCEEDED",
                message,
                HttpStatus.TOO_MANY_REQUESTS,
                new FieldDetail(field,rule));
    }
}
