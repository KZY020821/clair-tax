package com.clairtax.backend.auth.service;

import com.clairtax.backend.auth.config.AuthProperties;
import jakarta.mail.internet.InternetAddress;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("!local")
public class MagicLinkMailService {

    private static final String SUBJECT = "Your Clair Tax sign-in link";

    private final JavaMailSender javaMailSender;
    private final String senderEmail;
    private final AuthProperties authProperties;
    private final String htmlTemplate;
    private final String plainTextTemplate;

    public MagicLinkMailService(
            JavaMailSender javaMailSender,
            AuthProperties authProperties,
            ResourceLoader resourceLoader,
            @Value("${MAIL_USERNAME:no-reply@clairtax.local}") String senderEmail
    ) {
        this.javaMailSender = javaMailSender;
        this.authProperties = authProperties;
        this.senderEmail = senderEmail;
        this.htmlTemplate = loadTemplate(resourceLoader, "classpath:mail/magic-link.html");
        this.plainTextTemplate = loadTemplate(resourceLoader, "classpath:mail/magic-link.txt");
    }

    public void sendMagicLink(String recipientEmail, String verifyUrl) {
        try {
            var message = javaMailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            var placeholders = buildPlaceholders(verifyUrl);

            helper.setTo(recipientEmail);
            helper.setFrom(new InternetAddress(senderEmail, "Clair Tax").toString());
            helper.setSubject(SUBJECT);
            helper.setText(
                    renderTemplate(plainTextTemplate, placeholders),
                    renderTemplate(htmlTemplate, placeholders)
            );

            javaMailSender.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to send the magic link email", exception);
        }
    }

    private Map<String, String> buildPlaceholders(String verifyUrl) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("subject", SUBJECT);
        placeholders.put("verifyUrl", verifyUrl);
        placeholders.put("frontendBaseUrl", authProperties.getFrontendBaseUrl());
        placeholders.put("expiryLabel", formatDurationLabel(Duration.parse(authProperties.getMagicLinkTtl())));
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
}
