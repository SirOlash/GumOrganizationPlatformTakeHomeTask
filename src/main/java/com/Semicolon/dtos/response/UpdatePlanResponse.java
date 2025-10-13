package com.Semicolon.dtos.response;

import com.Semicolon.data.models.Plan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlanResponse {
    private Plan oldPlan;
    private Plan newPlan;
    private Instant updatedAt;
    private String message;
}
