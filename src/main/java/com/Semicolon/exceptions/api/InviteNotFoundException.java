package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class InviteNotFoundException extends ApiException{
    public InviteNotFoundException(String message) {
        super("INVITE_NOT_FOUND",
                message,
                HttpStatus.NOT_FOUND,
                new FieldDetail("Invite", "not found"));
    }
}
