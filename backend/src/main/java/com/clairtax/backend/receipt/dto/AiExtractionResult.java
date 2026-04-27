package com.clairtax.backend.receipt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiExtractionResult(
        @JsonProperty("extraction_status") String extractionStatus,
        @JsonProperty("amount")            String amount,
        @JsonProperty("currency")          String currency,
        @JsonProperty("date")              String date,
        @JsonProperty("merchant_name")     String merchantName,
        @JsonProperty("amount_confidence") Double amountConfidence,
        @JsonProperty("date_confidence")   Double dateConfidence,
        @JsonProperty("merchant_confidence") Double merchantConfidence,
        @JsonProperty("error_detail")      String errorDetail
) {}
