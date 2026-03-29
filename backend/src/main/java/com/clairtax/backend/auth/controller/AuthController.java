package com.clairtax.backend.auth.controller;

import com.clairtax.backend.auth.dto.AuthSessionResponse;
import com.clairtax.backend.auth.dto.MagicLinkRequestRequest;
import com.clairtax.backend.auth.dto.MagicLinkRequestResponse;
import com.clairtax.backend.auth.service.MagicLinkAuthService;
import com.clairtax.backend.auth.service.MagicLinkVerificationException;
import com.clairtax.backend.auth.service.SessionCookieService;
import com.clairtax.backend.auth.service.VerifiedMagicLinkSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@Profile("!local")
@Validated
@RequestMapping("/api/auth")
public class AuthController {

    private static final String MAGIC_LINK_SENT_MESSAGE =
            "If the email is valid, a sign-in link has been sent.";

    private final MagicLinkAuthService magicLinkAuthService;
    private final SessionCookieService sessionCookieService;

    public AuthController(
            MagicLinkAuthService magicLinkAuthService,
            SessionCookieService sessionCookieService
    ) {
        this.magicLinkAuthService = magicLinkAuthService;
        this.sessionCookieService = sessionCookieService;
    }

    @PostMapping("/magic-link/request")
    public MagicLinkRequestResponse requestMagicLink(
            @Valid @RequestBody MagicLinkRequestRequest request
    ) {
        String backendBaseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .build()
                .toUriString();
        magicLinkAuthService.requestMagicLink(request.email(), backendBaseUrl);
        return new MagicLinkRequestResponse(MAGIC_LINK_SENT_MESSAGE);
    }

    @GetMapping("/magic-link/verify")
    public ResponseEntity<Void> verifyMagicLink(
            @RequestParam @NotBlank String token
    ) {
        try {
            VerifiedMagicLinkSession authSession = magicLinkAuthService.verifyMagicLink(token);

            return ResponseEntity.status(302)
                    .header(HttpHeaders.SET_COOKIE, sessionCookieService.createSessionCookie(authSession.sessionToken()).toString())
                    .header(HttpHeaders.LOCATION, magicLinkAuthService.getFrontendBaseUrl())
                    .build();
        } catch (MagicLinkVerificationException exception) {
            String loginUrl = UriComponentsBuilder.fromUriString(magicLinkAuthService.getFrontendBaseUrl())
                    .path("/login")
                    .queryParam("magicLink", magicLinkAuthService.mapFailureReasonToQueryValue(exception.getReason()))
                    .build(true)
                    .toUriString();

            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, loginUrl)
                    .build();
        }
    }

    @GetMapping("/session")
    public AuthSessionResponse getSession(HttpServletRequest request) {
        return magicLinkAuthService.resolveSession(request)
                .map(session -> new AuthSessionResponse(true, session.userId(), session.email(), "session"))
                .orElseGet(() -> new AuthSessionResponse(false, null, null, "anonymous"));
    }

    @PostMapping("/logout")
    public ResponseEntity<MagicLinkRequestResponse> logout(HttpServletRequest request) {
        magicLinkAuthService.logout(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookieService.clearSessionCookie().toString())
                .body(new MagicLinkRequestResponse("Signed out."));
    }
}
