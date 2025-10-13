package com.Semicolon.dtos.request;

import com.Semicolon.data.models.Plan;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlanRequest {
    @NotNull(message = "plan cannot be null")
    private Plan plan;
}
