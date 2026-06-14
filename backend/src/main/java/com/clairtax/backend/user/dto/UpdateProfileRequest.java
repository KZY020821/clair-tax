package com.clairtax.backend.user.dto;

import com.clairtax.backend.user.entity.MaritalStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
        @NotNull Boolean isDisabled,
        @NotNull MaritalStatus maritalStatus,
        Boolean spouseDisabled,
        Boolean spouseWorking,
        Boolean hasChildren,
        @Pattern(
                regexp = "^[A-Z]{1,2}[0-9]{10,11}$",
                message = "TIN must begin with 1–2 uppercase letters followed by 10–11 digits (e.g. OG1234567890)"
        )
        String tin
) {
}
