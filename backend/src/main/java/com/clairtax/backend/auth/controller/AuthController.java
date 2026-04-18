package com.clairtax.backend.auth.controller;

import com.clairtax.backend.auth.config.AuthProperties;
import com.clairtax.backend.auth.dto.AuthSessionResponse;
import com.clairtax.backend.auth.dto.AuthErrorResponse;
import com.clairtax.backend.auth.dto.MagicLinkRequestRequest;
import com.clairtax.backend.auth.dto.MagicLinkRequestResponse;
import com.clairtax.backend.auth.dto.MagicLinkVerifyResponse;
import com.clairtax.backend.auth.dto.OtpRequestRequest;
import com.clairtax.backend.auth.dto.OtpRequestResponse;
import com.clairtax.backend.auth.dto.OtpVerifyRequest;
import com.clairtax.backend.auth.service.EmailOtpAuthService;
import com.clairtax.backend.auth.service.EmailOtpRateLimitException;
import com.clairtax.backend.auth.service.EmailOtpVerificationException;
import com.clairtax.backend.auth.service.MagicLinkAuthService;
import com.clairtax.backend.auth.service.MagicLinkVerificationException;
import com.clairtax.backend.auth.service.SessionCookieService;
import com.clairtax.backend.auth.service.VerifiedMagicLinkSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private static final String OTP_SENT_MESSAGE =
            "If the email is valid, a sign-in code has been sent.";

    private final MagicLinkAuthService magicLinkAuthService;
    private final EmailOtpAuthService emailOtpAuthService;
    private final SessionCookieService sessionCookieService;
    private final AuthProperties authProperties;

    public AuthController(
            MagicLinkAuthService magicLinkAuthService,
            EmailOtpAuthService emailOtpAuthService,
            SessionCookieService sessionCookieService,
            AuthProperties authProperties
    ) {
        this.magicLinkAuthService = magicLinkAuthService;
        this.emailOtpAuthService = emailOtpAuthService;
        this.sessionCookieService = sessionCookieService;
        this.authProperties = authProperties;
    }

    @PostMapping("/magic-link/request")
    public MagicLinkRequestResponse requestMagicLink(
            @Valid @RequestBody MagicLinkRequestRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String debugVerifyUrl = magicLinkAuthService.requestMagicLink(
                        request.email(),
                        resolvePublicAuthBaseUrl(httpServletRequest),
                        request.isMobileClient()
                )
                .orElse(null);
        return new MagicLinkRequestResponse(MAGIC_LINK_SENT_MESSAGE, debugVerifyUrl);
    }

    @PostMapping("/otp/request")
    public OtpRequestResponse requestOtp(
            @Valid @RequestBody OtpRequestRequest request,
            HttpServletRequest httpServletRequest
    ) {
        var result = emailOtpAuthService.requestOtp(
                request.email(),
                request.deviceId(),
                extractRequestIp(httpServletRequest)
        );
        return new OtpRequestResponse(OTP_SENT_MESSAGE, result.resendAvailableAt(), result.debugCode());
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        try {
            return ResponseEntity.ok(toVerifyResponse(
                    emailOtpAuthService.verifyOtp(request.email(), request.code(), request.deviceId())
            ));
        } catch (EmailOtpVerificationException exception) {
            return ResponseEntity.status(401).body(new AuthErrorResponse(
                    emailOtpAuthService.mapFailureReasonToMessage(exception.getReason())
            ));
        }
    }

    @GetMapping("/magic-link/verify")
    public ResponseEntity<?> verifyMagicLink(
            @RequestParam @NotBlank String token,
            @RequestHeader(value = "Accept", defaultValue = "text/html") String accept
    ) {
        try {
            VerifiedMagicLinkSession authSession = magicLinkAuthService.verifyMagicLink(token);

            // Mobile client: Return JSON response with session token
            if (accept.contains("application/json")) {
                return ResponseEntity.ok(toVerifyResponse(authSession));
            }

            // Web client: Redirect with cookie (existing behavior)
            return ResponseEntity.status(302)
                    .header(HttpHeaders.SET_COOKIE, sessionCookieService.createSessionCookie(authSession.sessionToken()).toString())
                    .header(HttpHeaders.LOCATION, magicLinkAuthService.getFrontendBaseUrl())
                    .build();
        } catch (MagicLinkVerificationException exception) {
            // Mobile client: Return JSON error
            if (accept.contains("application/json")) {
                return ResponseEntity.status(401).build();
            }

            // Web client: Redirect to login with error (existing behavior)
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

    @GetMapping(value = "/mobile-link", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> openMobileMagicLink(@RequestParam @NotBlank String token) {
        String appUrl = magicLinkAuthService.getMobileAppVerifyUrl(token);
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>Open Clair Tax</title>
                    <style>
                      :root {
                        color-scheme: light;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                      }
                      body {
                        margin: 0;
                        min-height: 100vh;
                        display: grid;
                        place-items: center;
                        background: #f5f9ff;
                        color: #000000;
                        padding: 24px;
                      }
                      main {
                        width: min(100%%, 480px);
                        background: #ffffff;
                        border: 1px solid #d6e0f1;
                        border-radius: 28px;
                        padding: 28px;
                        box-shadow: 0 24px 60px rgba(12, 28, 52, 0.08);
                      }
                      h1 {
                        margin: 0 0 12px;
                        font-size: 32px;
                        line-height: 1.1;
                      }
                      p {
                        margin: 0 0 16px;
                        line-height: 1.6;
                        color: #4f5866;
                      }
                      a {
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 48px;
                        padding: 0 20px;
                        border-radius: 999px;
                        background: #000000;
                        color: #ffffff;
                        text-decoration: none;
                        font-weight: 700;
                      }
                    </style>
                  </head>
                  <body>
                    <main>
                      <h1>Open Clair Tax</h1>
                      <p>We are handing this sign-in back to the Clair Tax app now. If nothing happens, use the button below to open the app manually.</p>
                      <a href="%s">Open Clair Tax</a>
                      <p style="margin-top: 18px;">Keep this browser tab open until the app finishes verifying the link. This page never consumes the token on its own.</p>
                    </main>
                    <script>
                      window.location.replace(%s);
                    </script>
                  </body>
                </html>
                """.formatted(appUrl, toJsStringLiteral(appUrl));
        return ResponseEntity.ok(html);
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

    @org.springframework.web.bind.annotation.ExceptionHandler(EmailOtpRateLimitException.class)
    public ResponseEntity<AuthErrorResponse> handleOtpRateLimit(EmailOtpRateLimitException exception) {
        return ResponseEntity.status(429).body(new AuthErrorResponse(exception.getMessage(), exception.getRetryAt()));
    }

    private MagicLinkVerifyResponse toVerifyResponse(VerifiedMagicLinkSession authSession) {
        return new MagicLinkVerifyResponse(
                authSession.sessionToken(),
                authSession.userId(),
                authSession.email(),
                authSession.expiresAt()
        );
    }

    private String resolvePublicAuthBaseUrl(HttpServletRequest request) {
        String configuredBaseUrl = authProperties.getPublicBaseUrl();
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl;
        }

        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .build()
                .toUriString();
    }

    private String extractRequestIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String toJsStringLiteral(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }
}
