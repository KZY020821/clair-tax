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
    public Optional<String> requestMagicLink(String email, String authLinkBaseUrl, boolean mobileClient) {
        String normalizedEmail = normalizeEmail(email);
        String rawToken = generateOpaqueToken();

        magicLinkTokenRepository.save(new MagicLinkToken(
                normalizedEmail,
                hashToken(rawToken),
                now().plus(Duration.parse(authProperties.getMagicLinkTtl()))
        ));

        String verifyUrl = UriComponentsBuilder.fromUriString(authLinkBaseUrl)
                .path(mobileClient ? "/api/auth/mobile-link" : "/api/auth/magic-link/verify")
                .queryParam("token", rawToken)
                .build(true)
                .toUriString();

        return magicLinkMailService.sendMagicLink(normalizedEmail, verifyUrl);
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

        return createSessionForUser(appUser, currentTimestamp);
    }

    @Transactional(readOnly = true)
    public Optional<AuthSessionView> resolveSession(HttpServletRequest request) {
        // Try Bearer token first (mobile)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && !authHeader.isBlank()) {
            Optional<AuthSessionView> bearerSession = resolveSessionFromBearer(authHeader);
            if (bearerSession.isPresent()) {
                return bearerSession;
            }
        }

        // Fall back to cookie (web)
        return resolveSession(readCookie(request, authProperties.getCookieName()));
    }

    @Transactional(readOnly = true)
    public Optional<AuthSessionView> resolveSessionFromBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String rawToken = authHeader.substring(7);
        return resolveSession(rawToken);
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
        String rawSessionToken = resolveRawSessionToken(request);
        if (rawSessionToken == null || rawSessionToken.isBlank()) {
            return;
        }

        revokeSession(rawSessionToken);
    }

    @Transactional
    public VerifiedMagicLinkSession createSessionForEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        OffsetDateTime currentTimestamp = now();
        AppUser appUser = appUserRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> appUserRepository.save(new AppUser(normalizedEmail)));
        appUser.markLoggedIn(currentTimestamp);
        return createSessionForUser(appUser, currentTimestamp);
    }

    @Transactional
    public void revokeSession(String rawSessionToken) {
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

    public String getMobileAppVerifyUrl(String token) {
        return UriComponentsBuilder.fromUriString(authProperties.getMobileAppScheme() + "://auth/verify")
                .queryParam("token", token)
                .build(true)
                .toUriString();
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

    private VerifiedMagicLinkSession createSessionForUser(AppUser appUser, OffsetDateTime currentTimestamp) {
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
                rawSessionToken,
                webSession.getExpiresAt().toInstant()
        );
    }

    private String resolveRawSessionToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return readCookie(request, authProperties.getCookieName());
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
