package com.clairtax.backend.user.service;

import com.clairtax.backend.user.config.DevUserProperties;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!local")
@Transactional(readOnly = true)
public class DevCurrentUserProvider implements CurrentUserProvider {

    private final AppUserRepository appUserRepository;
    private final DevUserProperties devUserProperties;

    public DevCurrentUserProvider(
            AppUserRepository appUserRepository,
            DevUserProperties devUserProperties
    ) {
        this.appUserRepository = appUserRepository;
        this.devUserProperties = devUserProperties;
    }

    @Override
    public CurrentUser getCurrentUser() {
        AppUser appUser = appUserRepository.findByEmail(devUserProperties.getEmail())
                .orElseThrow(() -> new IllegalStateException(
                        "Configured dev user " + devUserProperties.getEmail() + " was not found"
                ));

        return new CurrentUser(appUser.getId(), appUser.getEmail());
    }
}
