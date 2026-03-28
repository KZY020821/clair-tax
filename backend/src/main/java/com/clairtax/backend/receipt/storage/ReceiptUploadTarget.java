package com.clairtax.backend.receipt.storage;

import java.time.OffsetDateTime;
import java.util.Map;

public record ReceiptUploadTarget(
        String uploadUrl,
        String method,
        Map<String, String> headers,
        OffsetDateTime expiresAt
) {
}
