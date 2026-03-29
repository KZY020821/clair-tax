package com.clairtax.backend.auth;

import com.clairtax.backend.auth.entity.MagicLinkToken;
import com.clairtax.backend.auth.repository.MagicLinkTokenRepository;
import com.clairtax.backend.auth.repository.WebSessionRepository;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s\"'<>]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private MagicLinkTokenRepository magicLinkTokenRepository;

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
        jdbcTemplate.update("DELETE FROM users WHERE email <> ?", devUserEmail);
        capturingMailSender.clear();
    }

    @Test
    void requestMagicLinkReturnsGenericSuccessAndSendsEmail() throws Exception {
        mockMvc.perform(post("/api/auth/magic-link/request")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "person@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("If the email is valid, a sign-in link has been sent.")));

        MimeMessage lastMessage = capturingMailSender.getLastMessage();
        assertNotNull(lastMessage);
        assertTrue(lastMessage.getAllRecipients()[0].toString().contains("person@example.com"));
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
    void verifyMagicLinkCreatesUserSessionAndSupportsLogout() throws Exception {
        String email = "magic.user@example.com";
        String token = requestMagicLinkFor(email);

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
        String token = requestMagicLinkFor("reused@example.com");

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

    private String requestMagicLinkFor(String email) throws Exception {
        mockMvc.perform(post("/api/auth/magic-link/request")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s"
                                }
                                """.formatted(email)))
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
            try {
                mimeMessage.saveChanges();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to finalize the mime message", exception);
            }
            this.lastMessage = mimeMessage;
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
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

        void clear() {
            lastMessage = null;
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
