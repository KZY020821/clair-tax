package com.clairtax.backend.user.service;

import com.clairtax.backend.receipt.service.InternalReceiptApiAccessVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class CompositeCurrentUserProvider implements CurrentUserProvider {

    private final SessionCurrentUserProvider sessionProvider;
    private final InternalRequestCurrentUserProvider internalProvider;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public CompositeCurrentUserProvider(
            SessionCurrentUserProvider sessionProvider,
            InternalRequestCurrentUserProvider internalProvider,
            ObjectProvider<HttpServletRequest> requestProvider
    ) {
        this.sessionProvider = sessionProvider;
        this.internalProvider = internalProvider;
        this.requestProvider = requestProvider;
    }

    @Override
    public CurrentUser getCurrentUser() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request != null) {
            String internalToken = request.getHeader(InternalReceiptApiAccessVerifier.INTERNAL_TOKEN_HEADER);
            if (internalToken != null && !internalToken.isBlank()) {
                return internalProvider.resolveFromRequest(request);
            }
        }
        return sessionProvider.getCurrentUser();
    }
}
