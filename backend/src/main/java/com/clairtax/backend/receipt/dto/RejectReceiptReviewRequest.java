package com.clairtax.backend.receipt.dto;

import jakarta.validation.constraints.Size;

public record RejectReceiptReviewRequest(
        @Size(max = 2000) String notes,
        @Size(max = 120) String invalidReasonCode,
        @Size(max = 2000) String invalidReasonMessage
) {
}
