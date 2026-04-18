package com.clairtax.backend.user.controller;

import com.clairtax.backend.user.dto.CurrentUserResponse;
import com.clairtax.backend.user.config.DevUserProperties;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!local")
@RequestMapping("/api/dev")
public class DevUserController {

    private final AppUserRepository appUserRepository;
    private final DevUserProperties devUserProperties;

    public DevUserController(
            AppUserRepository appUserRepository,
            DevUserProperties devUserProperties
    ) {
        this.appUserRepository = appUserRepository;
        this.devUserProperties = devUserProperties;
    }

    @GetMapping("/me")
    public CurrentUserResponse getCurrentUser() {
        AppUser appUser = appUserRepository.findByEmail(devUserProperties.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new IllegalStateException(
                        "Configured dev user " + devUserProperties.getEmail() + " was not found"
                ));

        return new CurrentUserResponse(appUser.getId(), appUser.getEmail(), "dev");
    }
}
