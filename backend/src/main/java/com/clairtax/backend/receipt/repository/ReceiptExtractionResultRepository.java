package com.clairtax.backend.receipt.repository;

import com.clairtax.backend.receipt.entity.ReceiptExtractionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptExtractionResultRepository extends JpaRepository<ReceiptExtractionResult, UUID> {

    Optional<ReceiptExtractionResult> findTopByReceiptIdOrderByProcessedAtDesc(UUID receiptId);

    boolean existsByJobId(UUID jobId);

    void deleteByReceiptId(UUID receiptId);
}
