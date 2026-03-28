package com.clairtax.backend.receipt.repository;

import com.clairtax.backend.receipt.entity.ReceiptProcessingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptProcessingAttemptRepository extends JpaRepository<ReceiptProcessingAttempt, UUID> {

    Optional<ReceiptProcessingAttempt> findByJobId(UUID jobId);

    void deleteByReceiptId(UUID receiptId);
}
