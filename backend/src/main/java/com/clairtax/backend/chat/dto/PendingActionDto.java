package com.clairtax.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record PendingActionDto(
        @NotBlank String toolName,
        @NotNull Map<String, Object> toolArgs,
        String description
) {
}
