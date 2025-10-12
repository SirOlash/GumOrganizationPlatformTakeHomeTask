package com.Semicolon.dtos.response;

import com.Semicolon.data.models.InviteStatus;
import com.Semicolon.data.models.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteListItem {
    private UUID id;
    private String token;
    private Role role;
    private InviteStatus status;
    private Instant createdAt;
    private Instant expiresAt;
    private UUID organizationId;
}
