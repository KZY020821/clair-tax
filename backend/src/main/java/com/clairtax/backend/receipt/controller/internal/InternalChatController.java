package com.clairtax.backend.receipt.controller.internal;

import com.clairtax.backend.chat.dto.ChatAssignReceiptRequest;
import com.clairtax.backend.chat.dto.ChatProcessReceiptRequest;
import com.clairtax.backend.receipt.dto.ReceiptResponse;
import com.clairtax.backend.receipt.service.ReceiptService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/chat")
public class InternalChatController {

    private final ReceiptService receiptService;

    public InternalChatController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    /**
     * Assigns an existing receipt to a Year of Assessment workspace.
     * Authentication is handled by CompositeCurrentUserProvider via X-Clair-Internal-Token + X-User-Id.
     */
    @PostMapping("/receipts")
    public ResponseEntity<ReceiptResponse> assignReceiptToYear(
            @Valid @RequestBody ChatAssignReceiptRequest request
    ) {
        ReceiptResponse receipt = receiptService.assignReceiptToYear(
                request.receiptId(),
                request.year(),
                request.reliefCategoryId()
        );
        return ResponseEntity.ok(receipt);
    }

    /**
     * Processes a chat attachment: runs OCR, creates a verified receipt, and places it in the
     * correct Year of Assessment workspace.
     * Authentication is handled by CompositeCurrentUserProvider via X-Clair-Internal-Token + X-User-Id.
     */
    @PostMapping("/receipts/process")
    public ResponseEntity<ReceiptResponse> processReceiptFromChatAttachment(
            @Valid @RequestBody ChatProcessReceiptRequest request
    ) {
        ReceiptResponse receipt = receiptService.processReceiptFromChatAttachment(
                request.s3Key(),
                request.year(),
                request.reliefCategoryHint()
        );
        return ResponseEntity.ok(receipt);
    }
}
