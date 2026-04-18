package com.clairtax.backend.useryear.controller;

import com.clairtax.backend.receipt.dto.DirectReceiptUploadRequest;
import com.clairtax.backend.receipt.dto.ReceiptResponse;
import com.clairtax.backend.receipt.service.ReceiptService;
import com.clairtax.backend.useryear.dto.CreateUserYearRequest;
import com.clairtax.backend.useryear.dto.UserYearResponse;
import com.clairtax.backend.useryear.dto.UserYearWorkspaceResponse;
import com.clairtax.backend.useryear.service.UserYearService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping(value = "/{year}/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptResponse> uploadReceipt(
            @PathVariable Integer year,
            @RequestParam("merchantName") String merchantName,
            @RequestParam("receiptDate") LocalDate receiptDate,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(value = "reliefCategoryId", required = false) UUID reliefCategoryId,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestPart("file") MultipartFile file
    ) {
        DirectReceiptUploadRequest fields = new DirectReceiptUploadRequest(
                merchantName, receiptDate, amount, reliefCategoryId, notes
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(receiptService.uploadReceipt(year, fields, file));
    }
}
