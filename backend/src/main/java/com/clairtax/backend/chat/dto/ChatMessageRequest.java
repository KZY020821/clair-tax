package com.clairtax.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatMessageRequest(
        @NotBlank @Size(max = 2000) String content,
        @NotNull @Size(max = 20) List<AiChatMessage> history,
        @Size(max = 5) List<String> attachmentUrls,
        @Size(max = 5) List<String> attachmentS3Keys
) {
}
