package com.clairtax.backend.receipt.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ConfirmReceiptUploadRequest(
        @NotNull UUID uploadIntentId,
        @Size(max = 2000) String notes
) {
}
