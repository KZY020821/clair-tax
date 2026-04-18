package com.clairtax.backend.receipt.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ReceiptUploadIntentResponse(
        UUID uploadIntentId,
        String uploadUrl,
        String uploadMethod,
        Map<String, String> uploadHeaders,
        OffsetDateTime expiresAt
) {
}
