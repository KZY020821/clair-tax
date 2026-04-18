package com.clairtax.backend.auth;

import com.clairtax.backend.auth.entity.EmailOtpCode;
import com.clairtax.backend.auth.entity.MagicLinkToken;
import com.clairtax.backend.auth.repository.EmailOtpCodeRepository;
import com.clairtax.backend.auth.repository.MagicLinkTokenRepository;
import com.clairtax.backend.auth.repository.WebSessionRepository;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import com.clairtax.backend.IntegrationTestConfig;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "clair.auth.public-base-url=http://192.168.0.8:8080")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
class AuthControllerIntegrationTest {

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s\"'<>]+)");
    private static final Pattern OTP_CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private MagicLinkTokenRepository magicLinkTokenRepository;

    @Autowired
    private EmailOtpCodeRepository emailOtpCodeRepository;

    @Autowired
    private WebSessionRepository webSessionRepository;

    @Autowired
    private CapturingMailSender capturingMailSender;

    @Value("${clair.auth.cookie-name}")
    private String cookieName;

    @Value("${clair.dev-user.email}")
    private String devUserEmail;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM web_sessions");
        jdbcTemplate.update("DELETE FROM magic_link_tokens");
        jdbcTemplate.update("DELETE FROM email_otp_codes");
        jdbcTemplate.update("DELETE FROM users WHERE email <> ?", devUserEmail);
        capturingMailSender.clear();
    }

    @Test
    void requestMagicLinkReturnsGenericSuccessAndSendsEmail() throws Exception {
        mockMvc.perform(post("/api/auth/magic-link/request")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "person@example.com",
                                  "client": "web"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("If the email is valid, a sign-in link has been sent.")))
                .andExpect(jsonPath("$.debugVerifyUrl").doesNotExist());

        MimeMessage lastMessage = capturingMailSender.getLastMessage();
        assertNotNull(lastMessage);
        assertTrue(lastMessage.getAllRecipients()[0].toString().contains("person@example.com"));
        InternetAddress sender = (InternetAddress) lastMessage.getFrom()[0];
        assertTrue(sender.toUnicodeString().contains("clairtax.notification@gmail.com"));
        MailBodyParts bodyParts = extractMailBodyParts(lastMessage);
        assertNotNull(bodyParts.plainText());
        assertNotNull(bodyParts.htmlText());
        assertTrue(bodyParts.plainText().contains("/api/auth/magic-link/verify?token="));
        assertTrue(bodyParts.htmlText().contains("/api/auth/magic-link/verify?token="));
        assertTrue(bodyParts.htmlText().contains("Clair Tax"));
        assertTrue(bodyParts.htmlText().contains("Sign in to Clair Tax"));
        assertTrue(bodyParts.plainText().contains("expires in"));
    }

    @Test
    void requestMagicLinkReturnsDebugLinkWhenLocalMailDeliveryFails() throws Exception {
        capturingMailSender.failWith(new IllegalStateException("Simulated local mail delivery failure"));

        mockMvc.perform(post("/api/auth/magic-link/request")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "fallback@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("If the email is valid, a sign-in link has been sent.")))
                .andExpect(jsonPath("$.debugVerifyUrl", notNullValue()));
    }

    @Test
    void requestMobileMagicLinkUsesBridgeUrlAndConfiguredPublicBaseUrl() throws Exception {
        mockMvc.perform(post("/api/auth/magic-link/request")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "mobile@example.com",
                                  "client": "mobile"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("If the email is valid, a sign-in link has been sent.")));

        MailBodyParts bodyParts = extractMailBodyParts(capturingMailSender.getLastMessage());
        assertNotNull(bodyParts.plainText());
        assertTrue(bodyParts.plainText().contains("http://192.168.0.8:8080/api/auth/mobile-link?token="));
    }

    @Test
    void mobileMagicLinkBridgeReturnsAppOpenHtmlWithoutConsumingToken() throws Exception {
        String token = requestMagicLinkFor("bridge@example.com", "mobile");

        mockMvc.perform(get("/api/auth/mobile-link").param("token", token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/html")))
                .andExpect(content().string(containsString("clair-tax://auth/verify?token=" + token)));

        mockMvc.perform(get("/api/auth/magic-link/verify")
                        .param("token", token)
                        .header(HttpHeaders.ACCEPT, APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken", notNullValue()));
    }

    @Test
    void verifyMagicLinkCreatesUserSessionAndSupportsCookieLogout() throws Exception {
        String email = "magic.user@example.com";
        String token = requestMagicLinkFor(email, "web");

        var verifyResponse = mockMvc.perform(get("/api/auth/magic-link/verify").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, startsWith(cookieName + "=")))
                .andReturn()
                .getResponse();

        var sessionCookie = verifyResponse.getCookie(cookieName);
        assertNotNull(sessionCookie);

        AppUser appUser = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("Expected a user to be created"));
        assertNotNull(appUser.getLastLoginAt());

        mockMvc.perform(get("/api/auth/session").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.mode", is("session")));

        mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, startsWith(cookieName + "=")))
                .andExpect(jsonPath("$.message", is("Signed out.")));

        mockMvc.perform(get("/api/auth/session").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(false)))
                .andExpect(jsonPath("$.mode", is("anonymous")));

        webSessionRepository.findBySessionHash(sha256(sessionCookie.getValue()))
                .ifPresentOrElse(
                        session -> assertNotNull(session.getRevokedAt()),
                        () -> {
                            throw new AssertionError("Expected a persisted web session");
                        }
                );
    }

    @Test
    void verifyMagicLinkRejectsReusedTokens() throws Exception {
        String token = requestMagicLinkFor("reused@example.com", "web");

        mockMvc.perform(get("/api/auth/magic-link/verify").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost:3000"));

        mockMvc.perform(get("/api/auth/magic-link/verify").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost:3000/login?magicLink=used"));
    }

    @Test
    void verifyMagicLinkRejectsExpiredTokens() throws Exception {
        String rawToken = "expired-token-for-test";
        magicLinkTokenRepository.save(new MagicLinkToken(
                "expired@example.com",
                sha256(rawToken),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5)
        ));

        mockMvc.perform(get("/api/auth/magic-link/verify").param("token", rawToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost:3000/login?magicLink=expired"));
    }

    @Test
    void sessionEndpointReturnsAnonymousWithoutCookie() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(false)))
                .andExpect(jsonPath("$.mode", is("anonymous")));
    }

    @Test
    void verifyMagicLinkReturnsJsonSessionForMobileClientsAndSupportsBearerLogout() throws Exception {
        String token = requestMagicLinkFor("json.mobile@example.com", "mobile");

        String sessionToken = mockMvc.perform(get("/api/auth/magic-link/verify")
                        .param("token", token)
                        .header(HttpHeaders.ACCEPT, APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken", notNullValue()))
                .andExpect(jsonPath("$.email", is("json.mobile@example.com")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String extractedSessionToken = extractJsonValue(sessionToken, "sessionToken");

        mockMvc.perform(get("/api/auth/session")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + extractedSessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.email", is("json.mobile@example.com")));

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + extractedSessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Signed out.")));

        mockMvc.perform(get("/api/auth/session")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + extractedSessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(false)));
    }

    @Test
    void requestOtpReturnsGenericSuccessAndSendsEmail() throws Exception {
        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "otp@example.com",
                                  "deviceId": "ios-device"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("If the email is valid, a sign-in code has been sent.")))
                .andExpect(jsonPath("$.resendAvailableAt", notNullValue()));

        MailBodyParts bodyParts = extractMailBodyParts(capturingMailSender.getLastMessage());
        assertNotNull(bodyParts.plainText());
        Matcher matcher = OTP_CODE_PATTERN.matcher(bodyParts.plainText());
        assertTrue(matcher.find(), "Expected a 6-digit OTP in the email body");
    }

    @Test
    void verifyOtpCreatesUserSessionAndSupportsSessionChecks() throws Exception {
        String email = "otp.login@example.com";
        String code = requestOtpFor(email, "device-1");

        String responseBody = mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "code": "%s",
                                  "deviceId": "device-1"
                                }
                                """.formatted(email, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken", notNullValue()))
                .andExpect(jsonPath("$.email", is(email)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionToken = extractJsonValue(responseBody, "sessionToken");
        mockMvc.perform(get("/api/auth/session")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.email", is(email)));
    }

    @Test
    void verifyOtpRejectsInvalidCodesAndLocksAfterTooManyAttempts() throws Exception {
        String email = "otp.invalid@example.com";
        requestOtpFor(email, "device-2");

        for (int attempt = 1; attempt <= 4; attempt += 1) {
            mockMvc.perform(post("/api/auth/otp/verify")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "%s",
                                      "code": "000000",
                                      "deviceId": "device-2"
                                    }
                                    """.formatted(email)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message", is("That sign-in code is invalid. Check the email and try again.")));
        }

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "code": "000000",
                                  "deviceId": "device-2"
                                }
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("That sign-in code has been locked after too many attempts. Request a new code.")));
    }

    @Test
    void verifyOtpRejectsExpiredCodes() throws Exception {
        emailOtpCodeRepository.save(new EmailOtpCode(
                "expired.otp@example.com",
                "device-expired",
                "127.0.0.1",
                sha256("123456"),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        ));

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "expired.otp@example.com",
                                  "code": "123456",
                                  "deviceId": "device-expired"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("That sign-in code has expired. Request a fresh code to continue.")));
    }

    @Test
    void requestOtpRateLimitsAfterTooManyRequestsForOneEmail() throws Exception {
        for (int requestCount = 1; requestCount <= 5; requestCount += 1) {
            requestOtpFor("otp.limit@example.com", "device-limit-" + requestCount);
        }

        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "otp.limit@example.com",
                                  "deviceId": "device-limit-overflow"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", is("Too many codes have been requested for this email. Try again later.")))
                .andExpect(jsonPath("$.retryAt", notNullValue()));
    }

    private String requestMagicLinkFor(String email, String client) throws Exception {
        mockMvc.perform(post("/api/auth/magic-link/request")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "client": "%s"
                                }
                                """.formatted(email, client)))
                .andExpect(status().isOk());

        MimeMessage lastMessage = capturingMailSender.getLastMessage();
        assertNotNull(lastMessage);
        MailBodyParts body = extractMailBodyParts(lastMessage);
        assertNotNull(body.plainText());
        assertNotNull(body.htmlText());
        assertTrue(body.htmlText().contains("Magic Link Login"));
        assertTrue(body.plainText().contains("This sign-in link expires in"));
        String combinedBody = body.plainText() + "\n" + body.htmlText();
        Matcher matcher = URL_PATTERN.matcher(combinedBody);
        if (!matcher.find()) {
            throw new AssertionError("Expected a magic link URL in the email body");
        }

        URI magicLinkUri = URI.create(matcher.group(1));
        String query = magicLinkUri.getQuery();
        if (query == null || !query.startsWith("token=")) {
            throw new AssertionError("Expected the magic link to include a token query parameter");
        }

        return query.substring("token=".length());
    }

    private String requestOtpFor(String email, String deviceId) throws Exception {
        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "deviceId": "%s"
                                }
                                """.formatted(email, deviceId)))
                .andExpect(status().isOk());

        MimeMessage lastMessage = capturingMailSender.getLastMessage();
        assertNotNull(lastMessage);
        MailBodyParts body = extractMailBodyParts(lastMessage);
        assertNotNull(body.plainText());

        Matcher matcher = OTP_CODE_PATTERN.matcher(body.plainText());
        if (!matcher.find()) {
            throw new AssertionError("Expected an OTP code in the email body");
        }

        return matcher.group(1);
    }

    private MailBodyParts extractMailBodyParts(MimeMessage message) throws Exception {
        MailBodyPartsCollector collector = new MailBodyPartsCollector();
        collectMailBodyParts(message, collector);
        return collector.build();
    }

    private void collectMailBodyParts(Part part, MailBodyPartsCollector collector) throws Exception {
        Object content = part.getContent();

        if (content instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index += 1) {
                collectMailBodyParts(multipart.getBodyPart(index), collector);
            }
            return;
        }

        if (part.isMimeType("text/plain") && content instanceof String plainText) {
            collector.setPlainText(plainText);
            return;
        }

        if (part.isMimeType("text/html") && content instanceof String htmlText) {
            collector.setHtmlText(htmlText);
            return;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash the token", exception);
        }
    }

    private String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Expected key " + key + " in JSON: " + json);
        }
        return matcher.group(1);
    }

    @TestConfiguration
    static class MailTestConfiguration {

        @Bean
        @Primary
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }

    static class CapturingMailSender implements JavaMailSender {

        private MimeMessage lastMessage;
        private RuntimeException sendFailure;

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            try {
                return new MimeMessage(Session.getInstance(new Properties()), contentStream);
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to create the mime message", exception);
            }
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            if (sendFailure != null) {
                throw sendFailure;
            }
            try {
                mimeMessage.saveChanges();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to finalize the mime message", exception);
            }
            this.lastMessage = mimeMessage;
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            if (sendFailure != null) {
                throw sendFailure;
            }
            if (mimeMessages.length > 0) {
                try {
                    mimeMessages[mimeMessages.length - 1].saveChanges();
                } catch (Exception exception) {
                    throw new IllegalStateException("Failed to finalize the mime message", exception);
                }
                this.lastMessage = mimeMessages[mimeMessages.length - 1];
            }
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            throw new UnsupportedOperationException("Simple mail messages are not used in these tests");
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            throw new UnsupportedOperationException("Simple mail messages are not used in these tests");
        }

        MimeMessage getLastMessage() {
            return lastMessage;
        }

        void failWith(RuntimeException sendFailure) {
            this.sendFailure = sendFailure;
        }

        void clear() {
            lastMessage = null;
            sendFailure = null;
        }
    }

    private record MailBodyParts(String plainText, String htmlText) {
    }

    private static final class MailBodyPartsCollector {

        private String plainText;
        private String htmlText;

        private void setPlainText(String plainText) {
            if (this.plainText == null) {
                this.plainText = plainText;
            }
        }

        private void setHtmlText(String htmlText) {
            if (this.htmlText == null) {
                this.htmlText = htmlText;
            }
        }

        private MailBodyParts build() {
            return new MailBodyParts(plainText, htmlText);
        }
    }
}
