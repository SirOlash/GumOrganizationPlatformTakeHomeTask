package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class DateValidationException extends ApiException{
    public DateValidationException(String message){
        super("DATE_INVALID",
                message,
                HttpStatus.UNPROCESSABLE_ENTITY,
                    new FieldDetail("Date", "invalid"));

    }
}
