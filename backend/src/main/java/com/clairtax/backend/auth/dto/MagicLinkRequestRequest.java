package com.clairtax.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MagicLinkRequestRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @Pattern(regexp = "^(?i:web|mobile)?$", message = "client must be either web or mobile when provided")
        String client
) {
    public boolean isMobileClient() {
        return client != null && "mobile".equalsIgnoreCase(client.trim());
    }
}
