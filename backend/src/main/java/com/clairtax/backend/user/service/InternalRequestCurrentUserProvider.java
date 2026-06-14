package com.clairtax.backend.user.service;

import com.clairtax.backend.receipt.config.ReceiptProcessingProperties;
import com.clairtax.backend.receipt.service.InternalReceiptApiAccessVerifier;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class InternalRequestCurrentUserProvider {

    public static final String USER_ID_HEADER = "X-User-Id";

    private final AppUserRepository appUserRepository;
    private final ReceiptProcessingProperties properties;

    public InternalRequestCurrentUserProvider(
            AppUserRepository appUserRepository,
            ReceiptProcessingProperties properties
    ) {
        this.appUserRepository = appUserRepository;
        this.properties = properties;
    }

    public CurrentUser resolveFromRequest(HttpServletRequest request) {
        String providedToken = request.getHeader(InternalReceiptApiAccessVerifier.INTERNAL_TOKEN_HEADER);
        String expectedToken = properties.getInternalApiToken();
        if (providedToken == null || expectedToken == null || !providedToken.equals(expectedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal API token is invalid");
        }
        String userIdHeader = request.getHeader(USER_ID_HEADER);
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id header is required for internal requests");
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-User-Id is not a valid UUID");
        }
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return new CurrentUser(user.getId(), user.getEmail());
    }
}
