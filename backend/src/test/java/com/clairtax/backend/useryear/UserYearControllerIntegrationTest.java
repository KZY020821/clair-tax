package com.clairtax.backend.useryear;

import com.clairtax.backend.user.repository.AppUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserYearControllerIntegrationTest {

    private static final UUID POLICY_YEAR_2025_ID = UUID.fromString("21111111-1111-4111-8111-111111111111");
    private static final UUID POLICY_YEAR_2024_ID = UUID.fromString("21111111-1111-4111-8111-111111111112");
    private static final UUID POLICY_YEAR_2023_ID = UUID.fromString("21111111-1111-4111-8111-111111111113");
    private static final UUID LIFESTYLE_RELIEF_ID = UUID.fromString("54444444-4444-4444-8444-444444444541");
    private static final UUID MEDICAL_RELIEF_ID = UUID.fromString("54444444-4444-4444-8444-444444444542");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${clair.dev-user.email}")
    private String devUserEmail;

    @Value("${clair.receipts.internal-api-token}")
    private String internalApiToken;

    private UUID devUserId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM receipt_review_actions");
        jdbcTemplate.update("DELETE FROM receipt_extraction_results");
        jdbcTemplate.update("DELETE FROM receipt_processing_attempts");
        jdbcTemplate.update("DELETE FROM receipt_upload_intents");
        jdbcTemplate.update("DELETE FROM user_relief_claims");
        jdbcTemplate.update("DELETE FROM receipts");
        jdbcTemplate.update("DELETE FROM user_policy_years");
        jdbcTemplate.update("DELETE FROM relief_categories");
        jdbcTemplate.update("DELETE FROM policy_year");
        jdbcTemplate.update("DELETE FROM users WHERE email <> ?", devUserEmail);
        jdbcTemplate.update(
                """
                        UPDATE users
                        SET is_disabled = FALSE,
                            marital_status = 'single',
                            spouse_disabled = NULL,
                            spouse_working = NULL,
                            has_children = NULL
                        WHERE email = ?
                        """,
                devUserEmail
        );

        devUserId = appUserRepository.findByEmail(devUserEmail)
                .orElseThrow(() -> new AssertionError("Expected bootstrapped dev user to exist"))
                .getId();

        insertPolicyYear(POLICY_YEAR_2025_ID, 2025);
        insertPolicyYear(POLICY_YEAR_2024_ID, 2024);
        insertPolicyYear(POLICY_YEAR_2023_ID, 2023);

        insertRelief(LIFESTYLE_RELIEF_ID, POLICY_YEAR_2025_ID, "Lifestyle", "lifestyle_general", 2500.00, 10);
        insertRelief(MEDICAL_RELIEF_ID, POLICY_YEAR_2025_ID, "Medical", "medical_general", 1000.00, 20);
    }

    @Test
    void createAndListUserYearsReturnsOnlyCurrentUserYears() throws Exception {
        UUID otherUserId = insertOtherUser("other.year.user@taxrelief.local");
        insertUserPolicyYear(otherUserId, POLICY_YEAR_2024_ID);

        mockMvc.perform(post("/api/user-years")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "policyYear": 2025
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year", is(2025)));

        mockMvc.perform(post("/api/user-years")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "policyYear": 2023
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year", is(2023)));

        mockMvc.perform(get("/api/user-years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].year", is(2025)))
                .andExpect(jsonPath("$[1].year", is(2023)));
    }

    @Test
    void workspaceShowsCategorySummaryUploadReviewFlow() throws Exception {
        insertUserPolicyYear(devUserId, POLICY_YEAR_2025_ID);

        mockMvc.perform(get("/api/user-years/2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year", is(2025)))
                .andExpect(jsonPath("$.totalClaimedAmount", is(0)))
                .andExpect(jsonPath("$.totalReceiptCount", is(0)))
                .andExpect(jsonPath("$.categories.length()", is(2)));

        JsonNode uploadIntentResponse = objectMapper.readTree(
                mockMvc.perform(post("/api/user-years/{year}/receipts/upload-intent", 2025)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "reliefCategoryId": "%s",
                                          "fileName": "clinic.pdf",
                                          "mimeType": "application/pdf",
                                          "fileSizeBytes": 7
                                        }
                                        """.formatted(LIFESTYLE_RELIEF_ID)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        mockMvc.perform(put(uploadIntentResponse.get("uploadUrl").asText())
                        .contentType(MediaType.APPLICATION_PDF)
                        .content("receipt".getBytes()))
                .andExpect(status().isNoContent());

        JsonNode confirmedReceipt = objectMapper.readTree(
                mockMvc.perform(post("/api/user-years/{year}/receipts/confirm-upload", 2025)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "uploadIntentId": "%s",
                                          "notes": "Follow-up visit"
                                        }
                                        """.formatted(uploadIntentResponse.get("uploadIntentId").asText())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status", is("processing")))
                        .andExpect(jsonPath("$.amount").isEmpty())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        String receiptId = confirmedReceipt.get("id").asText();
        String jobId = jdbcTemplate.queryForObject(
                "SELECT job_id FROM receipt_processing_attempts WHERE receipt_id = ?",
                String.class,
                UUID.fromString(receiptId)
        );

        mockMvc.perform(post("/api/internal/receipts/{receiptId}/processing-attempts", receiptId)
                        .header("X-Clair-Internal-Token", internalApiToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": "%s",
                                  "status": "processing"
                                }
                                """.formatted(jobId)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/internal/receipts/{receiptId}/extraction-results", receiptId)
                        .header("X-Clair-Internal-Token", internalApiToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": "%s",
                                  "totalAmount": "120.50",
                                  "receiptDate": "2025-06-12",
                                  "merchantName": "Clinic",
                                  "currency": "MYR",
                                  "confidenceScore": "0.9600",
                                  "warnings": [],
                                  "rawPayloadJson": "{\\"provider\\":\\"test\\"}",
                                  "providerName": "test-provider",
                                  "providerVersion": "2026-03-27",
                                  "processedAt": "2026-03-27T00:00:00Z"
                                }
                                """.formatted(jobId)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/user-years/2025/receipts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].status", is("processed")))
                .andExpect(jsonPath("$[0].latestExtraction.totalAmount", is(120.50)))
                .andExpect(jsonPath("$[0].latestExtraction.merchantName", is("Clinic")));

        mockMvc.perform(post("/api/receipts/{receiptId}/review/confirm", receiptId)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("verified")))
                .andExpect(jsonPath("$.merchantName", is("Clinic")))
                .andExpect(jsonPath("$.amount", is(120.50)));

        mockMvc.perform(get("/api/user-years/2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClaimedAmount", is(120.50)))
                .andExpect(jsonPath("$.totalReceiptCount", is(1)))
                .andExpect(jsonPath("$.categories[0].claimedAmount", is(120.50)))
                .andExpect(jsonPath("$.categories[0].remainingAmount", is(2379.50)))
                .andExpect(jsonPath("$.categories[0].receiptCount", is(1)));
    }

    @Test
    void internalTrainingExportReturnsReviewedReceiptLabels() throws Exception {
        insertUserPolicyYear(devUserId, POLICY_YEAR_2025_ID);

        JsonNode verifiedUploadIntent = objectMapper.readTree(
                mockMvc.perform(post("/api/user-years/{year}/receipts/upload-intent", 2025)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "reliefCategoryId": "%s",
                                          "fileName": "verified.pdf",
                                          "mimeType": "application/pdf",
                                          "fileSizeBytes": 7
                                        }
                                        """.formatted(LIFESTYLE_RELIEF_ID)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        mockMvc.perform(put(verifiedUploadIntent.get("uploadUrl").asText())
                        .contentType(MediaType.APPLICATION_PDF)
                        .content("receipt".getBytes()))
                .andExpect(status().isNoContent());

        JsonNode verifiedReceipt = objectMapper.readTree(
                mockMvc.perform(post("/api/user-years/{year}/receipts/confirm-upload", 2025)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "uploadIntentId": "%s"
                                        }
                                        """.formatted(verifiedUploadIntent.get("uploadIntentId").asText())))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );
        String verifiedReceiptId = verifiedReceipt.get("id").asText();
        String verifiedJobId = jdbcTemplate.queryForObject(
                "SELECT job_id FROM receipt_processing_attempts WHERE receipt_id = ?",
                String.class,
                UUID.fromString(verifiedReceiptId)
        );

        mockMvc.perform(post("/api/internal/receipts/{receiptId}/extraction-results", verifiedReceiptId)
                        .header("X-Clair-Internal-Token", internalApiToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": "%s",
                                  "totalAmount": "88.90",
                                  "receiptDate": "2025-05-12",
                                  "merchantName": "Bookshop",
                                  "currency": "MYR",
                                  "confidenceScore": "0.9700",
                                  "warnings": [],
                                  "rawPayloadJson": "{\\"provider_payload\\":{\\"ExpenseDocuments\\":[]}}",
                                  "providerName": "test-provider",
                                  "providerVersion": "2026-03-27",
                                  "processedAt": "2026-03-27T00:00:00Z"
                                }
                                """.formatted(verifiedJobId)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/receipts/{receiptId}/review/confirm", verifiedReceiptId)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        JsonNode rejectedUploadIntent = objectMapper.readTree(
                mockMvc.perform(post("/api/user-years/{year}/receipts/upload-intent", 2025)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "reliefCategoryId": "%s",
                                          "fileName": "invalid.pdf",
                                          "mimeType": "application/pdf",
                                          "fileSizeBytes": 7
                                        }
                                        """.formatted(MEDICAL_RELIEF_ID)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        mockMvc.perform(put(rejectedUploadIntent.get("uploadUrl").asText())
                        .contentType(MediaType.APPLICATION_PDF)
                        .content("invalid".getBytes()))
                .andExpect(status().isNoContent());

        JsonNode rejectedReceipt = objectMapper.readTree(
                mockMvc.perform(post("/api/user-years/{year}/receipts/confirm-upload", 2025)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "uploadIntentId": "%s"
                                        }
                                        """.formatted(rejectedUploadIntent.get("uploadIntentId").asText())))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );
        String rejectedReceiptId = rejectedReceipt.get("id").asText();
        String rejectedJobId = jdbcTemplate.queryForObject(
                "SELECT job_id FROM receipt_processing_attempts WHERE receipt_id = ?",
                String.class,
                UUID.fromString(rejectedReceiptId)
        );

        mockMvc.perform(post("/api/internal/receipts/{receiptId}/extraction-results", rejectedReceiptId)
                        .header("X-Clair-Internal-Token", internalApiToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "jobId": "%s",
                                  "totalAmount": null,
                                  "receiptDate": null,
                                  "merchantName": null,
                                  "currency": "MYR",
                                  "confidenceScore": "0.2000",
                                  "warnings": ["Receipt was too blurry"],
                                  "rawPayloadJson": "{\\"provider_payload\\":{\\"ExpenseDocuments\\":[]}}",
                                  "providerName": "test-provider",
                                  "providerVersion": "2026-03-27",
                                  "processedAt": "2026-03-27T00:01:00Z",
                                  "errorCode": "unreadable_receipt",
                                  "errorMessage": "The uploaded file did not contain readable receipt text."
                                }
                                """.formatted(rejectedJobId)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/receipts/{receiptId}/review/reject", rejectedReceiptId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "notes": "Marked invalid after review",
                                  "invalidReasonCode": "unreadable_receipt",
                                  "invalidReasonMessage": "The uploaded file did not contain readable receipt text."
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode export = objectMapper.readTree(
                mockMvc.perform(get("/api/internal/receipts/training-examples")
                                .header("X-Clair-Internal-Token", internalApiToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        if (export.size() != 2) {
            throw new AssertionError("Expected two reviewed training examples");
        }

        JsonNode verifiedExample = null;
        JsonNode rejectedExample = null;
        for (JsonNode example : export) {
            if (example.get("receiptId").asText().equals(verifiedReceiptId)) {
                verifiedExample = example;
            }
            if (example.get("receiptId").asText().equals(rejectedReceiptId)) {
                rejectedExample = example;
            }
        }

        if (verifiedExample == null || rejectedExample == null) {
            throw new AssertionError("Expected both reviewed receipts in the export");
        }

        if (!verifiedExample.get("isValidReceipt").asBoolean()) {
            throw new AssertionError("Expected verified receipt export to be labeled valid");
        }
        if (Math.abs(verifiedExample.get("correctTotalAmount").asDouble() - 88.90) > 0.001) {
            throw new AssertionError("Expected verified export to include corrected amount");
        }
        if (!"2025-05-12".equals(verifiedExample.get("correctReceiptDate").asText())) {
            throw new AssertionError("Expected verified export to include corrected date");
        }
        if (rejectedExample.get("isValidReceipt").asBoolean()) {
            throw new AssertionError("Expected rejected receipt export to be labeled invalid");
        }
        if (!"unreadable_receipt".equals(rejectedExample.get("invalidReason").asText())) {
            throw new AssertionError("Expected rejected export to include the invalid reason code");
        }
    }

    @Test
    void workspaceUsesSavedProfileToFilterCategoriesAndApplyFixedClaims() throws Exception {
        UUID disabledReliefId = UUID.fromString("54444444-4444-4444-8444-444444444543");
        UUID childReliefId = UUID.fromString("54444444-4444-4444-8444-444444444544");
        insertRelief(disabledReliefId, POLICY_YEAR_2025_ID, "Disabled individual", "disabled_individual", 5_000.00, 15);
        insertRelief(childReliefId, POLICY_YEAR_2025_ID, "Child below 18", "child_below_18", 2_000.00, 25);

        jdbcTemplate.update(
                """
                        UPDATE users
                        SET is_disabled = TRUE,
                            marital_status = 'married',
                            spouse_disabled = FALSE,
                            spouse_working = FALSE,
                            has_children = FALSE
                        WHERE id = ?
                        """,
                devUserId
        );
        insertUserPolicyYear(devUserId, POLICY_YEAR_2025_ID);

        mockMvc.perform(get("/api/user-years/2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCategories", is(3)))
                .andExpect(jsonPath("$.totalClaimedAmount", is(5000.00)))
                .andExpect(jsonPath("$.categories[1].code", is("disabled_individual")))
                .andExpect(jsonPath("$.categories[1].claimedAmount", is(5000.00)))
                .andExpect(jsonPath("$.categories.length()", is(3)));
    }

    @Test
    void workspaceReturnsNotFoundWhenYearBelongsToAnotherUser() throws Exception {
        UUID otherUserId = insertOtherUser("hidden.year.user@taxrelief.local");
        insertUserPolicyYear(otherUserId, POLICY_YEAR_2025_ID);

        mockMvc.perform(get("/api/user-years/2025"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    private void insertPolicyYear(UUID id, int year) {
        jdbcTemplate.update(
                "INSERT INTO policy_year (id, year, status, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                id,
                year,
                "published"
        );
    }

    private void insertRelief(
            UUID id,
            UUID policyYearId,
            String name,
            String code,
            double maxAmount,
            int displayOrder
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO relief_categories (
                            id,
                            policy_year_id,
                            name,
                            description,
                            max_amount,
                            type,
                            code,
                            section,
                            input_type,
                            display_order,
                            auto_apply,
                            requires_receipt
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                policyYearId,
                name,
                name + " description",
                maxAmount,
                "lifestyle",
                code,
                "lifestyle",
                "amount",
                displayOrder,
                false,
                true
        );
    }

    private UUID insertOtherUser(String email) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            id,
                            email,
                            is_disabled,
                            marital_status,
                            spouse_disabled,
                            spouse_working,
                            has_children,
                            created_at,
                            last_login_at,
                            updated_at
                        ) VALUES (?, ?, FALSE, 'single', NULL, NULL, NULL, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP)
                        """,
                userId,
                email
        );
        return userId;
    }

    private void insertUserPolicyYear(UUID userId, UUID policyYearId) {
        jdbcTemplate.update(
                """
                        INSERT INTO user_policy_years (
                            id,
                            user_id,
                            policy_year_id,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                UUID.randomUUID(),
                userId,
                policyYearId
        );
    }
}
