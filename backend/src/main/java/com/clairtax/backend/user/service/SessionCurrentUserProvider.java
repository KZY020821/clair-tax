package com.clairtax.backend.user.service;

import com.clairtax.backend.auth.service.AuthSessionView;
import com.clairtax.backend.auth.service.MagicLinkAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SessionCurrentUserProvider implements CurrentUserProvider {

    private final MagicLinkAuthService magicLinkAuthService;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public SessionCurrentUserProvider(
            MagicLinkAuthService magicLinkAuthService,
            ObjectProvider<HttpServletRequest> requestProvider
    ) {
        this.magicLinkAuthService = magicLinkAuthService;
        this.requestProvider = requestProvider;
    }

    @Override
    public CurrentUser getCurrentUser() {
        HttpServletRequest request = requestProvider.getIfAvailable();

        if (request != null) {
            AuthSessionView session = magicLinkAuthService.resolveSession(request).orElse(null);
            if (session != null) {
                return new CurrentUser(session.userId(), session.email());
            }
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}
