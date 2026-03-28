package com.clairtax.backend.receipt.controller.internal;

import com.clairtax.backend.receipt.dto.internal.ReceiptExtractionResultRequest;
import com.clairtax.backend.receipt.dto.internal.ReceiptProcessingAttemptRequest;
import com.clairtax.backend.receipt.dto.internal.ReviewedReceiptTrainingExampleResponse;
import com.clairtax.backend.receipt.service.InternalReceiptApiAccessVerifier;
import com.clairtax.backend.receipt.service.ReceiptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Profile("!local")
@RequestMapping("/api/internal/receipts")
public class InternalReceiptController {

    private final ReceiptService receiptService;
    private final InternalReceiptApiAccessVerifier accessVerifier;

    public InternalReceiptController(
            ReceiptService receiptService,
            InternalReceiptApiAccessVerifier accessVerifier
    ) {
        this.receiptService = receiptService;
        this.accessVerifier = accessVerifier;
    }

    @GetMapping("/training-examples")
    public List<ReviewedReceiptTrainingExampleResponse> getTrainingExamples(
            HttpServletRequest httpServletRequest
    ) {
        accessVerifier.verify(httpServletRequest);
        return receiptService.exportReviewedReceiptTrainingExamples();
    }

    @PostMapping("/{receiptId}/processing-attempts")
    public ResponseEntity<Void> recordProcessingAttempt(
            @PathVariable UUID receiptId,
            @Valid @RequestBody ReceiptProcessingAttemptRequest request,
            HttpServletRequest httpServletRequest
    ) {
        accessVerifier.verify(httpServletRequest);
        receiptService.recordProcessingAttempt(receiptId, request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{receiptId}/extraction-results")
    public ResponseEntity<Void> recordExtractionResult(
            @PathVariable UUID receiptId,
            @Valid @RequestBody ReceiptExtractionResultRequest request,
            HttpServletRequest httpServletRequest
    ) {
        accessVerifier.verify(httpServletRequest);
        receiptService.recordExtractionResult(receiptId, request);
        return ResponseEntity.accepted().build();
    }
}
