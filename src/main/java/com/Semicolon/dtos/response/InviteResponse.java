package com.Semicolon.dtos.response;

import com.Semicolon.data.models.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteResponse {
    private String email;
    private Role role;
    private String token;
    private Instant createdAt;
    private Instant expiresAt;
}
