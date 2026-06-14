package com.clairtax.backend.chat.dto;

import java.util.List;

public record AiChatProcessRequest(
        String userId,
        String message,
        List<AiChatMessage> history,
        List<String> attachmentUrls,
        List<String> attachmentS3Keys
) {
}
