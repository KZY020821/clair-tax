package com.clairtax.backend.suggestion.dto;

import java.time.Instant;
import java.util.List;

public record SuggestionsListResponse(
        Integer policyYear,
        List<SuggestionResponse> suggestions,
        Instant generatedAt
) {
}
