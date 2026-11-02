package com.HemashreeVI.seatlock.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 254) String email,
            /* BCrypt only uses the first 72 bytes, so longer passwords are refused rather than silently cut */
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UserView(String id, String name, String email, List<String> roles, boolean guest) {
    }

    public record AuthResponse(String token, Instant expiresAt, UserView user) {
    }
}
