package com.clairtax.backend.chat.dto;

public record ChatConfirmResponse(
        String reply,
        boolean success,
        String error
) {
}
