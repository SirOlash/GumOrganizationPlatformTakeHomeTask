package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends ApiException{
    public EmailNotVerifiedException() {
        super("EMAIL_NOT_VERIFIED",
                "Verify with the token gotten from registration.",
                HttpStatus.FORBIDDEN,
                new FieldDetail("Email", "not verified"));
    }

}

