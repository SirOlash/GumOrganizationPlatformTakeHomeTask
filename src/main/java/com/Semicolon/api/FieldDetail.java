package com.Semicolon.api;

import lombok.Data;

@Data
public class FieldDetail {
    private String field;
    private String rule;

    public FieldDetail(String field, String rule) {
        this.field = field;
        this.rule = rule;
    }
}
