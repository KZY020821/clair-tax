package com.clairtax.backend.user.service;

import com.clairtax.backend.user.config.DevUserProperties;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!local")
public class DevUserBootstrap implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final DevUserProperties devUserProperties;

    public DevUserBootstrap(
            AppUserRepository appUserRepository,
            DevUserProperties devUserProperties
    ) {
        this.appUserRepository = appUserRepository;
        this.devUserProperties = devUserProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        appUserRepository.findByEmail(devUserProperties.getEmail())
                .orElseGet(() -> appUserRepository.save(new AppUser(devUserProperties.getEmail())));
    }
}
