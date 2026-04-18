package com.clairtax.backend.user.controller;

import com.clairtax.backend.user.dto.ProfileResponse;
import com.clairtax.backend.user.dto.UpdateProfileRequest;
import com.clairtax.backend.user.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse getProfile() {
        return profileService.getProfile();
    }

    @PutMapping
    public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(request);
    }

    @DeleteMapping("/account")
    public ResponseEntity<Void> resetAccount() {
        profileService.resetAccount();
        return ResponseEntity.noContent().build();
    }
}
