package com.clairtax.backend.receipt.repository;

import com.clairtax.backend.receipt.entity.ReceiptReviewAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptReviewActionRepository extends JpaRepository<ReceiptReviewAction, UUID> {

    Optional<ReceiptReviewAction> findTopByReceiptIdOrderByCreatedAtDesc(UUID receiptId);

    void deleteByReceiptId(UUID receiptId);
}
