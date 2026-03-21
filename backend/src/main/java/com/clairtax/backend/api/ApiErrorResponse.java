package com.clairtax.backend.api;

import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<ApiFieldErrorResponse> fieldErrors
) {

    public ApiErrorResponse {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
