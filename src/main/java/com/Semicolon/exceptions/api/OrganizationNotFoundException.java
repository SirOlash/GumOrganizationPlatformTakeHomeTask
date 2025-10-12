package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class OrganizationNotFoundException extends ApiException{
    public OrganizationNotFoundException(String message) {
        super("ORGANIZATION_NOT_FOUND",
                message,
                HttpStatus.NOT_FOUND,
                new FieldDetail("Organization", "not found"));
    }

}
