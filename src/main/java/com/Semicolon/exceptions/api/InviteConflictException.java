package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class InviteConflictException extends ApiException{
    public InviteConflictException(String message) {
        super("INVITE_CONFLICT",
                message,
                HttpStatus.CONFLICT,
                new FieldDetail("Invite", "conflict"));

    }
}
