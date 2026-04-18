package com.clairtax.backend.auth.service;

import com.clairtax.backend.auth.config.AuthProperties;
import com.clairtax.backend.auth.entity.EmailOtpCode;
import com.clairtax.backend.auth.repository.EmailOtpCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EmailOtpAuthService {

    private final EmailOtpCodeRepository emailOtpCodeRepository;
    private final MagicLinkMailService magicLinkMailService;
    private final MagicLinkAuthService magicLinkAuthService;
    private final AuthProperties authProperties;

    public EmailOtpAuthService(
            EmailOtpCodeRepository emailOtpCodeRepository,
            MagicLinkMailService magicLinkMailService,
            MagicLinkAuthService magicLinkAuthService,
            AuthProperties authProperties
    ) {
        this.emailOtpCodeRepository = emailOtpCodeRepository;
        this.magicLinkMailService = magicLinkMailService;
        this.magicLinkAuthService = magicLinkAuthService;
        this.authProperties = authProperties;
    }

    @Transactional
    public EmailOtpRequestResult requestOtp(String email, String deviceId, String requestIp) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        OffsetDateTime currentTimestamp = now();

        enforceRateLimits(normalizedEmail, normalizedDeviceId, requestIp, currentTimestamp);

        Optional<EmailOtpCode> latestExistingCode = findLatestCode(normalizedEmail, normalizedDeviceId);
        OffsetDateTime resendAvailableAt = latestExistingCode
                .map(EmailOtpCode::getCreatedAt)
                .orElse(currentTimestamp)
                .plus(Duration.parse(authProperties.getOtpResendCooldown()));
        if (latestExistingCode.isPresent() && currentTimestamp.isBefore(resendAvailableAt)) {
            throw new EmailOtpRateLimitException(
                    "Please wait before requesting another sign-in code.",
                    resendAvailableAt.toInstant()
            );
        }

        String rawCode = generateOtpCode();
        emailOtpCodeRepository.save(new EmailOtpCode(
                normalizedEmail,
                normalizedDeviceId,
                normalizeIp(requestIp),
                hashValue(rawCode),
                currentTimestamp.plus(Duration.parse(authProperties.getOtpTtl()))
        ));

        String debugCode = magicLinkMailService.sendOneTimePasscode(
                normalizedEmail,
                rawCode,
                Duration.parse(authProperties.getOtpTtl())
        ).orElse(null);

        return new EmailOtpRequestResult(
                currentTimestamp.plus(Duration.parse(authProperties.getOtpResendCooldown())).toInstant(),
                debugCode
        );
    }

    @Transactional(noRollbackFor = EmailOtpVerificationException.class)
    public VerifiedMagicLinkSession verifyOtp(String email, String code, String deviceId) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        OffsetDateTime currentTimestamp = now();
        EmailOtpCode emailOtpCode = findLatestCode(normalizedEmail, normalizedDeviceId)
                .orElseThrow(() -> new EmailOtpVerificationException(EmailOtpFailureReason.INVALID));

        if (emailOtpCode.isConsumed()) {
            throw new EmailOtpVerificationException(EmailOtpFailureReason.INVALID);
        }
        if (emailOtpCode.isExpiredAt(currentTimestamp)) {
            throw new EmailOtpVerificationException(EmailOtpFailureReason.EXPIRED);
        }
        if (emailOtpCode.getAttemptCount() >= authProperties.getOtpMaxAttempts()) {
            throw new EmailOtpVerificationException(EmailOtpFailureReason.TOO_MANY_ATTEMPTS);
        }
        if (!Objects.equals(emailOtpCode.getCodeHash(), hashValue(code))) {
            emailOtpCode.registerFailedAttempt(currentTimestamp);
            throw new EmailOtpVerificationException(
                    emailOtpCode.getAttemptCount() >= authProperties.getOtpMaxAttempts()
                            ? EmailOtpFailureReason.TOO_MANY_ATTEMPTS
                            : EmailOtpFailureReason.INVALID
            );
        }

        emailOtpCode.markConsumed(currentTimestamp);
        return magicLinkAuthService.createSessionForEmail(normalizedEmail);
    }

    public String mapFailureReasonToMessage(EmailOtpFailureReason reason) {
        return switch (reason) {
            case INVALID -> "That sign-in code is invalid. Check the email and try again.";
            case EXPIRED -> "That sign-in code has expired. Request a fresh code to continue.";
            case TOO_MANY_ATTEMPTS -> "That sign-in code has been locked after too many attempts. Request a new code.";
        };
    }

    private void enforceRateLimits(
            String email,
            String deviceId,
            String requestIp,
            OffsetDateTime currentTimestamp
    ) {
        OffsetDateTime windowStartedAt = currentTimestamp.minus(Duration.parse(authProperties.getOtpRateLimitWindow()));
        OffsetDateTime retryAt = currentTimestamp.plus(Duration.parse(authProperties.getOtpResendCooldown()));

        if (emailOtpCodeRepository.countByEmailAndCreatedAtAfter(email, windowStartedAt)
                >= authProperties.getOtpMaxRequestsPerEmail()) {
            throw new EmailOtpRateLimitException(
                    "Too many codes have been requested for this email. Try again later.",
                    retryAt.toInstant()
            );
        }

        String normalizedIp = normalizeIp(requestIp);
        if (normalizedIp != null
                && emailOtpCodeRepository.countByRequestIpAndCreatedAtAfter(normalizedIp, windowStartedAt)
                >= authProperties.getOtpMaxRequestsPerIp()) {
            throw new EmailOtpRateLimitException(
                    "Too many codes have been requested from this network. Try again later.",
                    retryAt.toInstant()
            );
        }

        if (deviceId != null
                && emailOtpCodeRepository.countByDeviceIdAndCreatedAtAfter(deviceId, windowStartedAt)
                >= authProperties.getOtpMaxRequestsPerDevice()) {
            throw new EmailOtpRateLimitException(
                    "Too many codes have been requested from this device. Try again later.",
                    retryAt.toInstant()
            );
        }
    }

    private Optional<EmailOtpCode> findLatestCode(String email, String deviceId) {
        return deviceId == null
                ? emailOtpCodeRepository.findFirstByEmailAndDeviceIdIsNullOrderByCreatedAtDesc(email)
                : emailOtpCodeRepository.findFirstByEmailAndDeviceIdOrderByCreatedAtDesc(email, deviceId);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDeviceId(String deviceId) {
        if (deviceId == null) {
            return null;
        }

        String trimmed = deviceId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeIp(String requestIp) {
        if (requestIp == null) {
            return null;
        }

        String trimmed = requestIp.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generateOtpCode() {
        int code = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return String.valueOf(code);
    }

    private String hashValue(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash the OTP code", exception);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
