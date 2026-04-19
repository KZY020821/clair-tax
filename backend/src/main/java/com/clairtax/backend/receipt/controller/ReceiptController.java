package com.clairtax.backend.receipt.controller;

import com.clairtax.backend.receipt.dto.ConfirmReceiptReviewRequest;
import com.clairtax.backend.receipt.dto.CreateReceiptRequest;
import com.clairtax.backend.receipt.dto.ReceiptResponse;
import com.clairtax.backend.receipt.dto.RejectReceiptReviewRequest;
import com.clairtax.backend.receipt.dto.ReplaceReceiptFileRequest;
import com.clairtax.backend.receipt.dto.UpdateReceiptRequest;
import com.clairtax.backend.receipt.service.ReceiptService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping("/years")
    public List<Integer> getReceiptYears() {
        return receiptService.getReceiptYears();
    }

    @GetMapping
    public List<ReceiptResponse> getReceipts(@RequestParam(required = false) Integer year) {
        return receiptService.getReceipts(year);
    }

    @GetMapping("/{id}")
    public ReceiptResponse getReceipt(@PathVariable UUID id) {
        return receiptService.getReceipt(id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadReceiptFile(@PathVariable UUID id) {
        ReceiptResponse receipt = receiptService.getReceipt(id);
        Resource resource = receiptService.loadReceiptFile(id);
        String fileName = receipt.fileName() == null ? "receipt-" + id : receipt.fileName();
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        "Content-Disposition",
                        ContentDisposition.inline().filename(fileName).build().toString()
                )
                .body(resource);
    }

    @PostMapping
    public ResponseEntity<ReceiptResponse> createReceipt(@Valid @RequestBody CreateReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(receiptService.createReceipt(request));
    }

    @PutMapping("/{id}")
    public ReceiptResponse updateReceipt(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReceiptRequest request
    ) {
        return receiptService.updateReceipt(id, request);
    }

    @PostMapping("/{id}/review/confirm")
    public ReceiptResponse confirmReceiptReview(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmReceiptReviewRequest request
    ) {
        return receiptService.confirmReview(id, request);
    }

    @PostMapping("/{id}/review/reject")
    public ReceiptResponse rejectReceiptReview(
            @PathVariable UUID id,
            @Valid @RequestBody RejectReceiptReviewRequest request
    ) {
        return receiptService.rejectReview(id, request);
    }

    @PatchMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReceiptResponse replaceReceiptFile(
            @PathVariable UUID id,
            @RequestParam("merchantName") String merchantName,
            @RequestParam("receiptDate") LocalDate receiptDate,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(value = "reliefCategoryId", required = false) UUID reliefCategoryId,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestPart("file") MultipartFile file
    ) {
        var fields = new ReplaceReceiptFileRequest(merchantName, receiptDate, amount, reliefCategoryId, notes);
        return receiptService.replaceReceiptFile(id, fields, file);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReceipt(@PathVariable UUID id) {
        receiptService.deleteReceipt(id);
        return ResponseEntity.noContent().build();
    }
}
