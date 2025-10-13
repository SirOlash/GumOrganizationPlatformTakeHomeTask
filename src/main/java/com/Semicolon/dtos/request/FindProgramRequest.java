package com.Semicolon.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FindProgramRequest {
    @NotBlank(message = "program name is required")
    private String ProgramName;

}
