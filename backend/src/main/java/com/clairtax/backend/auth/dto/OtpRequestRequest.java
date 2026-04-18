package com.clairtax.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OtpRequestRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 120) String deviceId
) {
}
