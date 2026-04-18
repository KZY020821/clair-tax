package com.clairtax.backend.user.dto;

import com.clairtax.backend.user.entity.MaritalStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateProfileRequest(
        @NotNull Boolean isDisabled,
        @NotNull MaritalStatus maritalStatus,
        Boolean spouseDisabled,
        Boolean spouseWorking,
        Boolean hasChildren
) {
}
