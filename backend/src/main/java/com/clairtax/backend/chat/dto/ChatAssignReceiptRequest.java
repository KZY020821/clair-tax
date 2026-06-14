package com.clairtax.backend.chat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChatAssignReceiptRequest(
        @NotNull UUID receiptId,
        @NotNull Integer year,
        UUID reliefCategoryId
) {
}
