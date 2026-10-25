package com.jpm.hrms.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {
    private UserDtos() {
    }

    /** Password policy: at least 8 chars with a letter and a digit. */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$";
    public static final String PASSWORD_MESSAGE = "must be 8+ characters and contain a letter and a digit";

    public record UserResponse(Long id, String username, String fullName, String email, Role role,
                               boolean enabled, Instant lastLoginAt, Instant createdAt) {
        public static UserResponse from(AppUser u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRole(),
                    u.isEnabled(), u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "^[A-Za-z0-9._@-]+$", message = "may only contain letters, digits and . _ @ -") String username,
            @NotBlank @Size(max = 150) String fullName,
            @Email @Size(max = 200) String email,
            @NotNull Role role,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Email @Size(max = 200) String email,
            @NotNull Role role,
            boolean enabled,
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
    }
}
