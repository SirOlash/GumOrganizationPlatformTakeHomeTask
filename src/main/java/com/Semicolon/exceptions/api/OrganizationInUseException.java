package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class OrganizationInUseException extends ApiException{
    public OrganizationInUseException() {
        super("ORG_IN_USE",
                "Organization already registered",
                HttpStatus.CONFLICT,
                new FieldDetail("Organization","in use"));
    }
}
