package com.clairtax.backend.health;

import com.clairtax.backend.IntegrationTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
class HealthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadsTestFileThroughReceiptStorageAndCleansUpByDefault() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "s3 connectivity test.pdf",
                "application/pdf",
                "receipt".getBytes()
        );

        mockMvc.perform(multipart("/api/health/receipt-storage/upload-test")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.storageBackend", containsString("IntegrationTestConfig")))
                .andExpect(jsonPath("$.usesS3Storage").value(false))
                .andExpect(jsonPath("$.bucketName").value("test-clair-tax-receipts"))
                .andExpect(jsonPath("$.objectKey", containsString("connectivity-tests/")))
                .andExpect(jsonPath("$.originalFileName").value("s3_connectivity_test.pdf"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.uploadedSizeBytes").value(7))
                .andExpect(jsonPath("$.storedSizeBytes").value(7))
                .andExpect(jsonPath("$.existsAfterUpload").value(true))
                .andExpect(jsonPath("$.cleanupAttempted").value(true))
                .andExpect(jsonPath("$.cleanedUp").value(true))
                .andExpect(jsonPath("$.cleanupError").isEmpty());
    }

    @Test
    void rejectsEmptyTestFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/health/receipt-storage/upload-test")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("A non-empty file is required for the storage upload test"));
    }
}
