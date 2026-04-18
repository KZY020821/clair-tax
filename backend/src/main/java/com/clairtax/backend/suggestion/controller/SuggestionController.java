package com.clairtax.backend.suggestion.controller;

import com.clairtax.backend.suggestion.dto.SuggestionResponse;
import com.clairtax.backend.suggestion.dto.SuggestionsListResponse;
import com.clairtax.backend.suggestion.service.SuggestionGenerationService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@Profile("!local")
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private final SuggestionGenerationService suggestionGenerationService;

    public SuggestionController(SuggestionGenerationService suggestionGenerationService) {
        this.suggestionGenerationService = suggestionGenerationService;
    }

    @GetMapping
    public SuggestionsListResponse getSuggestions(@RequestParam Integer policyYear) {
        List<SuggestionResponse> suggestions = suggestionGenerationService
                .getSuggestionsForCurrentUser(policyYear);

        return new SuggestionsListResponse(
                policyYear,
                suggestions,
                Instant.now()
        );
    }
}
