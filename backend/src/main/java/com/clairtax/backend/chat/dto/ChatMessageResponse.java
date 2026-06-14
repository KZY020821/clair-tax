package com.clairtax.backend.chat.dto;

public record ChatMessageResponse(
        String reply,
        PendingActionDto pendingAction,
        boolean requiresConfirmation
) {
}
