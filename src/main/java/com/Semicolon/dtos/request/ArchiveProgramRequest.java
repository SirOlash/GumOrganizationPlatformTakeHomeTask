package com.Semicolon.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveProgramRequest {
    @NotBlank(message = "program id is required")
    private String programName;
}
