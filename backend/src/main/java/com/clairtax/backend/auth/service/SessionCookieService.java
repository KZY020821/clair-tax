package com.clairtax.backend.auth.service;

import com.clairtax.backend.auth.config.AuthProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("!local")
public class SessionCookieService {

    private final AuthProperties authProperties;

    public SessionCookieService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public ResponseCookie createSessionCookie(String token) {
        return ResponseCookie.from(authProperties.getCookieName(), token)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .secure(false)
                .maxAge(Duration.parse(authProperties.getSessionTtl()))
                .build();
    }

    public ResponseCookie clearSessionCookie() {
        return ResponseCookie.from(authProperties.getCookieName(), "")
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .secure(false)
                .maxAge(Duration.ZERO)
                .build();
    }
}
