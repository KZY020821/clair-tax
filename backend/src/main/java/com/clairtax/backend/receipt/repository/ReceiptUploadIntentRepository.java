package com.clairtax.backend.receipt.repository;

import com.clairtax.backend.receipt.entity.ReceiptUploadIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptUploadIntentRepository extends JpaRepository<ReceiptUploadIntent, UUID> {

    Optional<ReceiptUploadIntent> findByIdAndUserPolicyYearUserId(UUID id, UUID userId);
}
