package com.clairtax.backend.receipt.controller;

import com.clairtax.backend.receipt.dto.CreateReceiptRequest;
import com.clairtax.backend.receipt.dto.ReceiptResponse;
import com.clairtax.backend.receipt.dto.UpdateReceiptRequest;
import com.clairtax.backend.receipt.service.ReceiptService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Profile("!local")
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReceipt(@PathVariable UUID id) {
        receiptService.deleteReceipt(id);
        return ResponseEntity.noContent().build();
    }
}
