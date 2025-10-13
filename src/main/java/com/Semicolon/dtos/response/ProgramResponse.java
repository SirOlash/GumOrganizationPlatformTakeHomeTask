package com.Semicolon.dtos.response;

import com.Semicolon.data.models.ProgramStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgramResponse {
    private UUID id;
    private UUID organizationId;
    private String name;
    private String description;
    private ProgramStatus status;
    private Instant startAt;
    private Instant endAt;
    private Instant createdAt;
    private Instant updatedAt;
}
