package com.clairtax.backend.auth.repository;

import com.clairtax.backend.auth.entity.EmailOtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailOtpCodeRepository extends JpaRepository<EmailOtpCode, UUID> {

    Optional<EmailOtpCode> findFirstByEmailAndDeviceIdOrderByCreatedAtDesc(String email, String deviceId);

    Optional<EmailOtpCode> findFirstByEmailAndDeviceIdIsNullOrderByCreatedAtDesc(String email);

    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime createdAt);

    long countByRequestIpAndCreatedAtAfter(String requestIp, OffsetDateTime createdAt);

    long countByDeviceIdAndCreatedAtAfter(String deviceId, OffsetDateTime createdAt);
}
