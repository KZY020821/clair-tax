package com.clairtax.backend.useryear.dto;

import jakarta.validation.constraints.NotNull;

public record CreateUserYearRequest(
        @NotNull Integer policyYear
) {
}
