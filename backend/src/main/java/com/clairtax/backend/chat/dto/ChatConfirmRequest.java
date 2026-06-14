package com.clairtax.backend.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ChatConfirmRequest(
        @NotNull @Valid PendingActionDto pendingAction
) {
}
