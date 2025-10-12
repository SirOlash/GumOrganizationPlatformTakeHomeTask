package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class InviteForbiddenException extends ApiException{
    public InviteForbiddenException() {
        super("INVITE_FORBIDDEN",
                "You don't have permission to invite this user",
                HttpStatus.FORBIDDEN,
                new FieldDetail("Invite", "forbidden"));
    }
    public InviteForbiddenException(String message) {
        super("INVITE_FORBIDDEN",
                message,
                HttpStatus.FORBIDDEN,
                new FieldDetail("Invite", "forbidden"));
    }
}
