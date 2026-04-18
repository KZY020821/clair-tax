package com.clairtax.backend.user.dto;

import com.clairtax.backend.user.entity.MaritalStatus;

import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String email,
        boolean isDisabled,
        MaritalStatus maritalStatus,
        Boolean spouseDisabled,
        Boolean spouseWorking,
        Boolean hasChildren
) {
}
