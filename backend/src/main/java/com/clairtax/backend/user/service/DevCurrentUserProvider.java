package com.clairtax.backend.user.service;

import com.clairtax.backend.auth.service.AuthSessionView;
import com.clairtax.backend.auth.service.MagicLinkAuthService;
import com.clairtax.backend.user.config.DevUserProperties;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!local")
public class DevCurrentUserProvider implements CurrentUserProvider {

    private final AppUserRepository appUserRepository;
    private final DevUserProperties devUserProperties;
    private final MagicLinkAuthService magicLinkAuthService;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public DevCurrentUserProvider(
            AppUserRepository appUserRepository,
            DevUserProperties devUserProperties,
            MagicLinkAuthService magicLinkAuthService,
            ObjectProvider<HttpServletRequest> requestProvider
    ) {
        this.appUserRepository = appUserRepository;
        this.devUserProperties = devUserProperties;
        this.magicLinkAuthService = magicLinkAuthService;
        this.requestProvider = requestProvider;
    }

    @Override
    public CurrentUser getCurrentUser() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request != null) {
            AuthSessionView authSession = magicLinkAuthService.resolveSession(request)
                    .orElse(null);

            if (authSession != null) {
                return new CurrentUser(authSession.userId(), authSession.email());
            }
        }

        AppUser appUser = appUserRepository.findByEmail(devUserProperties.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException(
                        "Configured dev user " + devUserProperties.getEmail() + " was not found"
                ));

        return new CurrentUser(appUser.getId(), appUser.getEmail());
    }
}
