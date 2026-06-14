package com.clairtax.backend.chat.controller;

import com.clairtax.backend.chat.dto.ChatConfirmRequest;
import com.clairtax.backend.chat.dto.ChatConfirmResponse;
import com.clairtax.backend.chat.dto.ChatMessageRequest;
import com.clairtax.backend.chat.dto.ChatMessageResponse;
import com.clairtax.backend.chat.service.ChatProxyService;
import com.clairtax.backend.user.service.CurrentUser;
import com.clairtax.backend.user.service.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatProxyService chatProxyService;
    private final CurrentUserProvider currentUserProvider;

    public ChatController(ChatProxyService chatProxyService, CurrentUserProvider currentUserProvider) {
        this.chatProxyService = chatProxyService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/message")
    public ChatMessageResponse sendMessage(@Valid @RequestBody ChatMessageRequest request) {
        CurrentUser user = currentUserProvider.getCurrentUser();
        return chatProxyService.processMessage(user.id(), request);
    }

    @PostMapping("/confirm")
    public ChatConfirmResponse confirmAction(@Valid @RequestBody ChatConfirmRequest request) {
        CurrentUser user = currentUserProvider.getCurrentUser();
        return chatProxyService.confirmAction(user.id(), request);
    }
}
