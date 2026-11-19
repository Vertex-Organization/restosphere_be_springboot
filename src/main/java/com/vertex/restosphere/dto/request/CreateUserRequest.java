package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 2, max = 150) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull Role role) {

    public CreateUserRequest {
        email = email == null ? null : email.strip();
    }
}
