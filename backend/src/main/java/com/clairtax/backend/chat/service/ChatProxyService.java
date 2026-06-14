package com.clairtax.backend.chat.service;

import com.clairtax.backend.chat.dto.AiChatConfirmRequest;
import com.clairtax.backend.chat.dto.AiChatMessage;
import com.clairtax.backend.chat.dto.AiChatProcessRequest;
import com.clairtax.backend.chat.dto.ChatConfirmRequest;
import com.clairtax.backend.chat.dto.ChatConfirmResponse;
import com.clairtax.backend.chat.dto.ChatMessageRequest;
import com.clairtax.backend.chat.dto.ChatMessageResponse;
import com.clairtax.backend.receipt.config.AiServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class ChatProxyService {

    private static final Logger logger = LoggerFactory.getLogger(ChatProxyService.class);

    private final AiServiceProperties aiProperties;
    private final RestTemplate restTemplate;

    public ChatProxyService(AiServiceProperties aiProperties) {
        this.aiProperties = aiProperties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiProperties.getConnectTimeoutSeconds() * 1000);
        factory.setReadTimeout(aiProperties.getChatReadTimeoutSeconds() * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    public ChatMessageResponse processMessage(UUID userId, ChatMessageRequest request) {
        String url = aiProperties.getBaseUrl() + "/internal/chat/process";
        List<AiChatMessage> history = request.history() != null ? request.history() : List.of();
        List<String> attachmentUrls = request.attachmentUrls() != null ? request.attachmentUrls() : List.of();
        List<String> attachmentS3Keys = request.attachmentS3Keys() != null ? request.attachmentS3Keys() : List.of();
        AiChatProcessRequest aiRequest = new AiChatProcessRequest(
                userId.toString(),
                request.content(),
                history,
                attachmentUrls,
                attachmentS3Keys
        );
        try {
            ResponseEntity<ChatMessageResponse> response =
                    restTemplate.postForEntity(url, toHttpEntity(aiRequest), ChatMessageResponse.class);
            ChatMessageResponse body = response.getBody();
            if (body == null) {
                return fallbackMessageResponse();
            }
            return body;
        } catch (RestClientException e) {
            logger.warn("AI chat service unreachable during processMessage: {}", e.getMessage());
            return fallbackMessageResponse();
        } catch (RuntimeException e) {
            logger.error("Unexpected error during processMessage: {}", e.getMessage(), e);
            return fallbackMessageResponse();
        }
    }

    public ChatConfirmResponse confirmAction(UUID userId, ChatConfirmRequest request) {
        String url = aiProperties.getBaseUrl() + "/internal/chat/confirm";
        AiChatConfirmRequest aiRequest = new AiChatConfirmRequest(
                userId.toString(),
                request.pendingAction()
        );
        try {
            ResponseEntity<ChatConfirmResponse> response =
                    restTemplate.postForEntity(url, toHttpEntity(aiRequest), ChatConfirmResponse.class);
            ChatConfirmResponse body = response.getBody();
            if (body == null) {
                return new ChatConfirmResponse("Action could not be completed.", false, "Empty response from AI service");
            }
            return body;
        } catch (RestClientException e) {
            logger.warn("AI chat service unreachable during confirmAction: {}", e.getMessage());
            return new ChatConfirmResponse("Action could not be completed. Please try again.", false, e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Unexpected error during confirmAction: {}", e.getMessage(), e);
            return new ChatConfirmResponse("Action could not be completed. Please try again.", false, e.getMessage());
        }
    }

    private <T> HttpEntity<T> toHttpEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private ChatMessageResponse fallbackMessageResponse() {
        return new ChatMessageResponse(
                "I'm having trouble connecting right now. Please try again.",
                null,
                false
        );
    }
}
