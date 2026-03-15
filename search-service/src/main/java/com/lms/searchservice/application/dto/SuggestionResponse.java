package com.lms.searchservice.application.dto;

import java.util.List;

/**
 * SuggestionResponse — autocomplete title suggestions.
 */
public record SuggestionResponse(List<String> suggestions) {}
