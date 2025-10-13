package com.Semicolon.dtos.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProgramRequest {
    @NotBlank(message = "name is required")
    @Size(max = 255)
    private String name;

    @Size(max = 2000)
    private String description;

    @FutureOrPresent(message = "start date must be in the future")
    private Instant startAt;
    private Instant endAt;
}
