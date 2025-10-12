package com.Semicolon.dtos.response;

import com.Semicolon.data.models.Plan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationProfileResponse {
    private UUID id;
    private String name;
    private String logoUrl;
    private String website;
    private String industry;
    private String description;
    private int membersCount;
    private int programsCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Plan plan;
    private int completenessPercent;
    private List<String> missingFields;

}
