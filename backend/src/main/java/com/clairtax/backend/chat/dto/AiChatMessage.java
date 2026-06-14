package com.clairtax.backend.chat.dto;

import java.util.List;

public record AiChatMessage(
        String role,
        String content,
        List<String> attachmentUrls
) {
}
