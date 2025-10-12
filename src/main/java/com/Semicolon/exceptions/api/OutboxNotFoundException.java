package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class OutboxNotFoundException extends ApiException{
    public OutboxNotFoundException() {
        super("OUTBOX_NOT_FOUND",
                "Outbox not found",
                HttpStatus.NOT_FOUND,
                new FieldDetail("OutBox", "not found"));
    }

}

