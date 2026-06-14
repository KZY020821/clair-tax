package com.clairtax.backend.chat.controller;

import com.clairtax.backend.chat.dto.ChatAttachmentResponse;
import com.clairtax.backend.receipt.storage.ReceiptObjectStorageService;
import com.clairtax.backend.user.service.CurrentUser;
import com.clairtax.backend.user.service.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatAttachmentController {

    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf");
    private static final Duration PRESIGNED_TTL = Duration.ofHours(2);

    private final ReceiptObjectStorageService storage;
    private final CurrentUserProvider currentUserProvider;

    public ChatAttachmentController(ReceiptObjectStorageService storage, CurrentUserProvider currentUserProvider) {
        this.storage = storage;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ChatAttachmentResponse uploadAttachment(@RequestParam MultipartFile file) throws IOException {
        CurrentUser user = currentUserProvider.getCurrentUser();

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds 20 MB limit");
        }
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_TYPES.contains(mimeType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String sanitized = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        String key = "chat-attachments/" + user.id() + "/" + UUID.randomUUID() + "-" + sanitized;

        storage.storeUploadedObject(key, file.getInputStream());

        String url = storage.generatePresignedGetUrl(key, PRESIGNED_TTL);
        if (url == null) {
            url = "local://" + key;
        }

        return new ChatAttachmentResponse(url, originalName, mimeType, file.getSize(), key);
    }
}
