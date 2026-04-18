package com.clairtax.backend.auth.service;

import com.clairtax.backend.auth.config.AuthProperties;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MagicLinkMailService {

    private static final String MAGIC_LINK_SUBJECT = "Your Clair Tax sign-in link";
    private static final String OTP_SUBJECT = "Your Clair Tax sign-in code";
    private static final Logger log = LoggerFactory.getLogger(MagicLinkMailService.class);

    private final JavaMailSender javaMailSender;
    private final String senderEmail;
    private final String smtpUsername;
    private final String smtpPassword;
    private final AuthProperties authProperties;
    private final String magicLinkHtmlTemplate;
    private final String magicLinkPlainTextTemplate;
    private final String otpHtmlTemplate;
    private final String otpPlainTextTemplate;

    public MagicLinkMailService(
            JavaMailSender javaMailSender,
            AuthProperties authProperties,
            ResourceLoader resourceLoader,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${spring.mail.password:}") String smtpPassword,
            @Value("${clair.auth.mail-from:${spring.mail.username:no-reply@clairtax.local}}") String senderEmail
    ) {
        this.javaMailSender = javaMailSender;
        this.authProperties = authProperties;
        this.smtpUsername = smtpUsername == null ? "" : smtpUsername.trim();
        this.smtpPassword = smtpPassword == null ? "" : smtpPassword.trim();
        this.senderEmail = senderEmail;
        this.magicLinkHtmlTemplate = loadTemplate(resourceLoader, "classpath:mail/magic-link.html");
        this.magicLinkPlainTextTemplate = loadTemplate(resourceLoader, "classpath:mail/magic-link.txt");
        this.otpHtmlTemplate = loadTemplate(resourceLoader, "classpath:mail/otp-code.html");
        this.otpPlainTextTemplate = loadTemplate(resourceLoader, "classpath:mail/otp-code.txt");

        // Validate SMTP configuration at startup
        if (smtpPassword != null && smtpPassword.contains(" ")) {
            log.warn("⚠️  SMTP password contains spaces! Gmail app passwords should have ALL spaces removed.");
            log.warn("Example: 'abcd efgh ijkl mnop' should be 'abcdefghijklmnop'");
        }

        if (hasConfiguredSmtpCredentials()) {
            log.info("SMTP mail delivery configured for user: {}", maskEmail(this.smtpUsername));
        } else {
            log.info("SMTP credentials not configured - will use debug links on localhost");
        }
    }

    public Optional<String> sendMagicLink(String recipientEmail, String verifyUrl) {
        if (isLocalDebugFallbackAllowed(verifyUrl) && !hasConfiguredSmtpCredentials()) {
            log.warn(
                    "Skipping SMTP delivery for {} on localhost because spring.mail.username/password are not configured. Falling back to an in-app debug link.",
                    recipientEmail
            );
            log.info("Local magic link for {}: {}", recipientEmail, verifyUrl);
            return Optional.of(verifyUrl);
        }

        try {
            var placeholders = buildPlaceholders(verifyUrl);
            sendTemplatedEmail(
                    recipientEmail,
                    MAGIC_LINK_SUBJECT,
                    renderTemplate(magicLinkPlainTextTemplate, placeholders),
                    renderTemplate(magicLinkHtmlTemplate, placeholders)
            );
            log.info("✓ Magic link email sent successfully to {}", recipientEmail);
            return Optional.empty();
        } catch (AuthenticationFailedException authException) {
            String errorMsg = String.format(
                    "SMTP Authentication failed for %s - Check your credentials",
                    maskEmail(smtpUsername)
            );

            if (isLocalDebugFallbackAllowed(verifyUrl)) {
                log.warn("Failed to deliver the magic link email to {} locally (Authentication failed). Falling back to an in-app debug link.", recipientEmail);
                log.error(errorMsg);
                log.error("💡 Hint: Gmail app passwords should NOT contain spaces");
                log.error("   Example: 'abcd efgh ijkl mnop' → 'abcdefghijklmnop'");
                log.info("Local magic link for {}: {}", recipientEmail, verifyUrl);
                return Optional.of(verifyUrl);
            }
            log.error(errorMsg, authException);
            throw new IllegalStateException("SMTP authentication failed - check spring.mail.username and spring.mail.password", authException);
        } catch (Exception exception) {
            if (isLocalDebugFallbackAllowed(verifyUrl)) {
                log.warn(
                        "Failed to deliver the magic link email to {} locally ({}). Falling back to an in-app debug link.",
                        recipientEmail,
                        exception.getMessage()
                );
                log.warn(
                        "Configure spring.mail.username and spring.mail.password to restore SMTP delivery on localhost. Recipient: {}",
                        recipientEmail
                );
                log.info("Local magic link for {}: {}", recipientEmail, verifyUrl);
                return Optional.of(verifyUrl);
            }
            throw new IllegalStateException("Failed to send the magic link email", exception);
        }
    }

    public Optional<String> sendOneTimePasscode(String recipientEmail, String code, Duration ttl) {
        if (!hasConfiguredSmtpCredentials() && isDebugFallbackAllowedForCurrentEnvironment()) {
            log.warn(
                    "Skipping SMTP delivery for {} because spring.mail.username/password are not configured. Falling back to an in-app debug OTP.",
                    recipientEmail
            );
            log.info("Local OTP for {}: {}", recipientEmail, code);
            return Optional.of(code);
        } else if (!hasConfiguredSmtpCredentials()) {
            throw new IllegalStateException("SMTP credentials are not configured for OTP delivery");
        }

        try {
            var placeholders = buildOtpPlaceholders(code, ttl);
            sendTemplatedEmail(
                    recipientEmail,
                    OTP_SUBJECT,
                    renderTemplate(otpPlainTextTemplate, placeholders),
                    renderTemplate(otpHtmlTemplate, placeholders)
            );
            log.info("✓ OTP email sent successfully to {}", recipientEmail);
            return Optional.empty();
        } catch (AuthenticationFailedException authException) {
            if (!isDebugFallbackAllowedForCurrentEnvironment()) {
                throw new IllegalStateException("SMTP authentication failed - check spring.mail.username and spring.mail.password", authException);
            }
            log.warn("Failed to deliver the OTP email to {} locally (Authentication failed). Falling back to an in-app debug OTP.", recipientEmail);
            log.error("SMTP Authentication failed for {} - Check your credentials", maskEmail(smtpUsername));
            log.error("💡 Hint: Gmail app passwords should NOT contain spaces");
            log.error("   Example: 'abcd efgh ijkl mnop' → 'abcdefghijklmnop'");
            log.info("Local OTP for {}: {}", recipientEmail, code);
            return Optional.of(code);
        } catch (Exception exception) {
            if (!isDebugFallbackAllowedForCurrentEnvironment()) {
                throw new IllegalStateException("Failed to send the OTP email", exception);
            }
            log.warn(
                    "Failed to deliver the OTP email to {} ({}). Falling back to an in-app debug OTP.",
                    recipientEmail,
                    exception.getMessage()
            );
            log.info("Local OTP for {}: {}", recipientEmail, code);
            return Optional.of(code);
        }
    }

    private Map<String, String> buildPlaceholders(String verifyUrl) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("subject", MAGIC_LINK_SUBJECT);
        placeholders.put("verifyUrl", verifyUrl);
        placeholders.put("frontendBaseUrl", authProperties.getFrontendBaseUrl());
        placeholders.put("expiryLabel", formatDurationLabel(Duration.parse(authProperties.getMagicLinkTtl())));
        return placeholders;
    }

    private Map<String, String> buildOtpPlaceholders(String code, Duration ttl) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("subject", OTP_SUBJECT);
        placeholders.put("code", code);
        placeholders.put("expiryLabel", formatDurationLabel(ttl));
        return placeholders;
    }

    private String renderTemplate(String template, Map<String, String> placeholders) {
        String rendered = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    private String loadTemplate(ResourceLoader resourceLoader, String location) {
        try (var inputStream = resourceLoader.getResource(location).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load the mail template: " + location, exception);
        }
    }

    private void sendTemplatedEmail(
            String recipientEmail,
            String subject,
            String plainTextBody,
            String htmlBody
    ) throws Exception {
        var message = javaMailSender.createMimeMessage();
        var helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(recipientEmail);
        helper.setFrom(new InternetAddress(senderEmail, "Clair Tax").toString());
        helper.setSubject(subject);
        helper.setText(plainTextBody, htmlBody);

        javaMailSender.send(message);
    }

    private String formatDurationLabel(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "a short time";
        }

        long totalDays = duration.toDays();
        duration = duration.minusDays(totalDays);
        long totalHours = duration.toHours();
        duration = duration.minusHours(totalHours);
        long totalMinutes = duration.toMinutes();
        duration = duration.minusMinutes(totalMinutes);
        long totalSeconds = duration.getSeconds();

        List<String> segments = new java.util.ArrayList<>();
        if (totalDays > 0) {
            segments.add(pluralize(totalDays, "day"));
        }
        if (totalHours > 0) {
            segments.add(pluralize(totalHours, "hour"));
        }
        if (totalMinutes > 0) {
            segments.add(pluralize(totalMinutes, "minute"));
        }
        if (segments.isEmpty() && totalSeconds > 0) {
            segments.add(pluralize(totalSeconds, "second"));
        }

        if (segments.isEmpty()) {
            return "a short time";
        }

        return segments.size() == 1
                ? segments.getFirst()
                : segments.get(0) + " " + segments.get(1);
    }

    private String pluralize(long value, String unit) {
        return value + " " + unit + (value == 1 ? "" : "s");
    }

    private boolean isLocalDebugFallbackAllowed(String verifyUrl) {
        try {
            String host = URI.create(verifyUrl).getHost();
            return isPrivateOrLoopbackHost(host);
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isDebugFallbackAllowedForCurrentEnvironment() {
        String publicBaseUrl = authProperties.getPublicBaseUrl();
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            try {
                return isPrivateOrLoopbackHost(URI.create(publicBaseUrl).getHost());
            } catch (Exception exception) {
                return false;
            }
        }

        try {
            return isPrivateOrLoopbackHost(URI.create(authProperties.getFrontendBaseUrl()).getHost());
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isPrivateOrLoopbackHost(String host) {
        if (host == null) {
            return false;
        }

        if ("localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "[::1]".equals(host)
                || "::1".equals(host)) {
            return true;
        }

        return host.startsWith("10.")
                || host.startsWith("192.168.")
                || host.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*$");
    }

    private boolean hasConfiguredSmtpCredentials() {
        return !smtpUsername.isBlank() && !smtpPassword.isBlank();
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "[not configured]";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email.substring(0, Math.min(3, email.length())) + "***";
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() <= 3) {
            return localPart.charAt(0) + "***" + domain;
        }
        return localPart.substring(0, 3) + "***" + domain;
    }
}
