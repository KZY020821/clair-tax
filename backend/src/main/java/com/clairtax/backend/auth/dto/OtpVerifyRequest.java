package com.clairtax.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpVerifyRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "code must be a 6-digit number") String code,
        @Size(max = 120) String deviceId
) {
}
