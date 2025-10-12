package com.Semicolon.dtos.request;

import com.Semicolon.data.models.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateInviteRequest {
    @Email(message = "must be a valid email")
    @NotNull(message = "email is required")
    private String email;

    @NotNull(message = "role is required")
    private Role role;
}
