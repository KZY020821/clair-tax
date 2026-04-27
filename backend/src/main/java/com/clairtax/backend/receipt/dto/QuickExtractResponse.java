package com.clairtax.backend.receipt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record QuickExtractResponse(
        UUID receiptId,
        String merchantName,
        LocalDate receiptDate,
        BigDecimal amount,
        String currency
) {}
