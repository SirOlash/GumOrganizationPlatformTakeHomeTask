package com.Semicolon.exceptions.api;

import com.Semicolon.api.FieldDetail;
import org.springframework.http.HttpStatus;

public class PlanDowngradeBlockedException extends ApiException{
    public PlanDowngradeBlockedException (String rule){
        super("PLAN_DOWNGRADE_BLOCKED",
                "Downgrade blocked",
                HttpStatus.BAD_REQUEST,
                new FieldDetail("Downgrade", rule));

    }
}
