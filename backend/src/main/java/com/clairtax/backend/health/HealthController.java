package com.clairtax.backend.health;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final ReceiptStorageConnectivityService receiptStorageConnectivityService;

    public HealthController(ReceiptStorageConnectivityService receiptStorageConnectivityService) {
        this.receiptStorageConnectivityService = receiptStorageConnectivityService;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("ok", "backend");
    }

    @PostMapping(value = "/receipt-storage/upload-test", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptStorageUploadTestResponse> uploadReceiptStorageTest(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "keepFile", defaultValue = "false") boolean keepFile
    ) {
        return ResponseEntity.ok(receiptStorageConnectivityService.uploadTestFile(file, keepFile));
    }
}
