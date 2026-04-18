package com.clairtax.backend.useryear.controller;

import com.clairtax.backend.receipt.dto.ReceiptResponse;
import com.clairtax.backend.receipt.dto.ConfirmReceiptUploadRequest;
import com.clairtax.backend.receipt.dto.CreateReceiptUploadIntentRequest;
import com.clairtax.backend.receipt.dto.ReceiptUploadIntentResponse;
import com.clairtax.backend.receipt.service.ReceiptService;
import com.clairtax.backend.useryear.dto.CreateUserYearRequest;
import com.clairtax.backend.useryear.dto.UserYearResponse;
import com.clairtax.backend.useryear.dto.UserYearWorkspaceResponse;
import com.clairtax.backend.useryear.service.UserYearService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/user-years")
public class UserYearController {

    private final UserYearService userYearService;
    private final ReceiptService receiptService;

    public UserYearController(
            UserYearService userYearService,
            ReceiptService receiptService
    ) {
        this.userYearService = userYearService;
        this.receiptService = receiptService;
    }

    @GetMapping
    public List<UserYearResponse> getUserYears() {
        return userYearService.getUserYears();
    }

    @PostMapping
    public UserYearResponse createUserYear(@Valid @RequestBody CreateUserYearRequest request) {
        return userYearService.createUserYear(request);
    }

    @GetMapping("/{year}")
    public UserYearWorkspaceResponse getWorkspace(@PathVariable Integer year) {
        return userYearService.getWorkspace(year);
    }

    @GetMapping("/{year}/receipts")
    public List<ReceiptResponse> getReceipts(@PathVariable Integer year) {
        return receiptService.getReceiptsForUserYear(year);
    }

    @PostMapping("/{year}/receipts/upload-intent")
    public ResponseEntity<ReceiptUploadIntentResponse> createUploadIntent(
            @PathVariable Integer year,
            @Valid @RequestBody CreateReceiptUploadIntentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(receiptService.createUploadIntent(year, request));
    }

    @PostMapping("/{year}/receipts/confirm-upload")
    public ResponseEntity<ReceiptResponse> confirmUpload(
            @PathVariable Integer year,
            @Valid @RequestBody ConfirmReceiptUploadRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(receiptService.confirmUpload(year, request));
    }
}
