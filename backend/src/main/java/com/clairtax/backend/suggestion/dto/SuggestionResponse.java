package com.clairtax.backend.suggestion.dto;

import com.clairtax.backend.suggestion.model.SuggestionPriority;
import com.clairtax.backend.suggestion.model.SuggestionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SuggestionResponse(
        UUID id,
        UUID reliefCategoryId,
        String reliefCategoryName,
        String reliefCategoryCode,
        BigDecimal suggestedAmount,
        BigDecimal currentClaimedAmount,
        String reason,
        List<UUID> supportingReceiptIds,
        SuggestionPriority priority,
        SuggestionType suggestionType
) {
}
