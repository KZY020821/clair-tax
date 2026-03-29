package com.clairtax.backend.auth.service;

import com.clairtax.backend.auth.config.AuthProperties;
import com.clairtax.backend.auth.entity.MagicLinkToken;
import com.clairtax.backend.auth.entity.WebSession;
import com.clairtax.backend.auth.repository.MagicLinkTokenRepository;
import com.clairtax.backend.auth.repository.WebSessionRepository;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
@Profile("!local")
public class MagicLinkAuthService {

    private final MagicLinkTokenRepository magicLinkTokenRepository;
    private final WebSessionRepository webSessionRepository;
    private final AppUserRepository appUserRepository;
    private final MagicLinkMailService magicLinkMailService;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public MagicLinkAuthService(
            MagicLinkTokenRepository magicLinkTokenRepository,
            WebSessionRepository webSessionRepository,
            AppUserRepository appUserRepository,
            MagicLinkMailService magicLinkMailService,
            AuthProperties authProperties
    ) {
        this.magicLinkTokenRepository = magicLinkTokenRepository;
        this.webSessionRepository = webSessionRepository;
        this.appUserRepository = appUserRepository;
        this.magicLinkMailService = magicLinkMailService;
        this.authProperties = authProperties;
    }

    @Transactional
    public void requestMagicLink(String email, String backendBaseUrl) {
        String normalizedEmail = normalizeEmail(email);
        String rawToken = generateOpaqueToken();

        magicLinkTokenRepository.save(new MagicLinkToken(
                normalizedEmail,
                hashToken(rawToken),
                now().plus(Duration.parse(authProperties.getMagicLinkTtl()))
        ));

        String verifyUrl = UriComponentsBuilder.fromUriString(backendBaseUrl)
                .path("/api/auth/magic-link/verify")
                .queryParam("token", rawToken)
                .build(true)
                .toUriString();

        magicLinkMailService.sendMagicLink(normalizedEmail, verifyUrl);
    }

    @Transactional
    public VerifiedMagicLinkSession verifyMagicLink(String rawToken) {
        MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new MagicLinkVerificationException(MagicLinkFailureReason.INVALID));

        OffsetDateTime currentTimestamp = now();
        if (magicLinkToken.isUsed()) {
            throw new MagicLinkVerificationException(MagicLinkFailureReason.USED);
        }
        if (magicLinkToken.isExpiredAt(currentTimestamp)) {
            throw new MagicLinkVerificationException(MagicLinkFailureReason.EXPIRED);
        }

        AppUser appUser = appUserRepository.findByEmail(magicLinkToken.getEmail())
                .orElseGet(() -> appUserRepository.save(new AppUser(magicLinkToken.getEmail())));
        appUser.markLoggedIn(currentTimestamp);

        magicLinkToken.markUsed(currentTimestamp);

        String rawSessionToken = generateOpaqueToken();
        WebSession webSession = webSessionRepository.save(new WebSession(
                appUser,
                hashToken(rawSessionToken),
                currentTimestamp.plus(Duration.parse(authProperties.getSessionTtl())),
                currentTimestamp
        ));

        return new VerifiedMagicLinkSession(
                webSession.getId(),
                appUser.getId(),
                appUser.getEmail(),
                rawSessionToken
        );
    }

    @Transactional(readOnly = true)
    public Optional<AuthSessionView> resolveSession(HttpServletRequest request) {
        return resolveSession(readCookie(request, authProperties.getCookieName()));
    }

    @Transactional(readOnly = true)
    public Optional<AuthSessionView> resolveSession(String rawSessionToken) {
        if (rawSessionToken == null || rawSessionToken.isBlank()) {
            return Optional.empty();
        }

        OffsetDateTime currentTimestamp = now();
        return webSessionRepository.findBySessionHash(hashToken(rawSessionToken))
                .filter(session -> !session.isRevoked())
                .filter(session -> !session.isExpiredAt(currentTimestamp))
                .map(session -> new AuthSessionView(
                        session.getId(),
                        session.getUser().getId(),
                        session.getUser().getEmail()
                ));
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        String rawSessionToken = readCookie(request, authProperties.getCookieName());
        if (rawSessionToken == null || rawSessionToken.isBlank()) {
            return;
        }

        webSessionRepository.findBySessionHash(hashToken(rawSessionToken))
                .ifPresent(session -> session.revoke(now()));
    }

    public String getFrontendBaseUrl() {
        return authProperties.getFrontendBaseUrl();
    }

    public String getCookieName() {
        return authProperties.getCookieName();
    }

    public String mapFailureReasonToQueryValue(MagicLinkFailureReason reason) {
        return switch (reason) {
            case INVALID -> "invalid";
            case EXPIRED -> "expired";
            case USED -> "used";
        };
    }

    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash the auth token", exception);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String readCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
