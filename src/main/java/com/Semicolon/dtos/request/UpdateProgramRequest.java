package com.Semicolon.dtos.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProgramRequest {
    @Size(max = 255)
    private String name;

    @Size(max = 2000)
    private String description;

    private Instant startAt;
    private Instant endAt;

    private Boolean archived;
}
