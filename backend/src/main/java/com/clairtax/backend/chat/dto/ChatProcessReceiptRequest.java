package com.clairtax.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatProcessReceiptRequest(
        @NotBlank String s3Key,
        @NotNull Integer year,
        String reliefCategoryHint
) {
}
