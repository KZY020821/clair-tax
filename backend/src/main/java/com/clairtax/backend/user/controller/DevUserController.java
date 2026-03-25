package com.clairtax.backend.user.controller;

import com.clairtax.backend.user.dto.CurrentUserResponse;
import com.clairtax.backend.user.service.CurrentUser;
import com.clairtax.backend.user.service.CurrentUserProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!local")
@RequestMapping("/api/dev")
public class DevUserController {

    private final CurrentUserProvider currentUserProvider;

    public DevUserController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/me")
    public CurrentUserResponse getCurrentUser() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        return new CurrentUserResponse(currentUser.id(), currentUser.email(), "dev");
    }
}
