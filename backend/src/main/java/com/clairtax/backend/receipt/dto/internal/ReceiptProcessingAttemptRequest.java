package com.clairtax.backend.receipt.dto.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReceiptProcessingAttemptRequest(
        @NotNull UUID jobId,
        @NotBlank String status,
        @Size(max = 120) String errorCode,
        @Size(max = 2000) String errorMessage
) {
}
