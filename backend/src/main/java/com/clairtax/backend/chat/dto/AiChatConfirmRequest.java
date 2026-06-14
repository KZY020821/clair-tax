package com.clairtax.backend.chat.dto;

public record AiChatConfirmRequest(
        String userId,
        PendingActionDto pendingAction
) {
}
