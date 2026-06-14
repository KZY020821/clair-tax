package com.clairtax.backend.chat.dto;

public record ChatAttachmentResponse(String url, String fileName, String mimeType, long fileSizeBytes, String s3Key) {
}
