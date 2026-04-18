package com.clairtax.backend.receipt;

import com.clairtax.backend.user.repository.AppUserRepository;
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

import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceiptControllerIntegrationTest {

    private static final UUID POLICY_YEAR_2025_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID POLICY_YEAR_2024_ID = UUID.fromString("11111111-1111-4111-8111-111111111114");
    private static final UUID POLICY_YEAR_2023_ID = UUID.fromString("11111111-1111-4111-8111-111111111113");
    private static final UUID RELIEF_2025_ID = UUID.fromString("44444444-4444-4444-8444-444444444541");
    private static final UUID RELIEF_2024_ID = UUID.fromString("44444444-4444-4444-8444-444444444542");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Value("${clair.dev-user.email}")
    private String devUserEmail;

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

        insertRelief(RELIEF_2025_ID, POLICY_YEAR_2025_ID, "Lifestyle 2025", "lifestyle_2025", 10);
        insertRelief(RELIEF_2024_ID, POLICY_YEAR_2024_ID, "Lifestyle 2024", "lifestyle_2024", 20);
    }

    @Test
    void createReceiptAssignsCurrentDevUser() throws Exception {
        mockMvc.perform(post("/api/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptPayload(2025, RELIEF_2025_ID, "Bookshop Sdn Bhd", "2025-05-12", "89.90")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyYear", is(2025)))
                .andExpect(jsonPath("$.merchantName", is("Bookshop Sdn Bhd")))
                .andExpect(jsonPath("$.reliefCategoryId", is(RELIEF_2025_ID.toString())))
                .andExpect(jsonPath("$.reliefCategoryName", is("Lifestyle 2025")));

        UUID storedUserId = jdbcTemplate.queryForObject(
                """
                        SELECT user_policy_years.user_id
                        FROM receipts
                        JOIN user_policy_years ON user_policy_years.id = receipts.user_policy_year_id
                        WHERE receipts.merchant_name = ?
                        """,
                UUID.class,
                "Bookshop Sdn Bhd"
        );

        if (!devUserId.equals(storedUserId)) {
            throw new AssertionError("Expected created receipt to belong to the dev user");
        }
    }

    @Test
    void listYearsReturnsOnlyYearsForCurrentDevUser() throws Exception {
        UUID otherUserId = insertOtherUser("another.dev@taxrelief.local");

        insertReceipt(UUID.fromString("77777777-7777-4777-8777-777777777781"), devUserId, 2024, "Camera Store", "2024-03-01", "400.00", RELIEF_2024_ID);
        insertReceipt(UUID.fromString("77777777-7777-4777-8777-777777777782"), devUserId, 2025, "Clinic", "2025-06-10", "250.00", RELIEF_2025_ID);
        insertReceipt(UUID.fromString("77777777-7777-4777-8777-777777777783"), otherUserId, 2023, "Other User Receipt", "2023-01-09", "20.00", null);

        mockMvc.perform(get("/api/receipts/years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", containsInRelativeOrder(2025, 2024)))
                .andExpect(jsonPath("$.length()", is(2)));
    }

    @Test
    void listReceiptsByYearReturnsOnlyCurrentUserReceiptsForThatYear() throws Exception {
        UUID otherUserId = insertOtherUser("other.user@taxrelief.local");

        insertReceipt(UUID.fromString("77777777-7777-4777-8777-777777777784"), devUserId, 2025, "Bookshop", "2025-06-01", "120.00", RELIEF_2025_ID);
        insertReceipt(UUID.fromString("77777777-7777-4777-8777-777777777785"), devUserId, 2024, "Pharmacy", "2024-04-01", "60.00", RELIEF_2024_ID);
        insertReceipt(UUID.fromString("77777777-7777-4777-8777-777777777786"), otherUserId, 2025, "Other User Bookshop", "2025-07-01", "220.00", RELIEF_2025_ID);

        mockMvc.perform(get("/api/receipts").param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].merchantName", is("Bookshop")))
                .andExpect(jsonPath("$[0].policyYear", is(2025)));
    }

    @Test
    void updateReceiptUpdatesExistingReceiptForCurrentUser() throws Exception {
        UUID receiptId = UUID.fromString("77777777-7777-4777-8777-777777777787");
        insertReceipt(receiptId, devUserId, 2025, "Original Merchant", "2025-02-01", "150.00", RELIEF_2025_ID);

        mockMvc.perform(put("/api/receipts/{id}", receiptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyYear": 2024,
                                  "merchantName": "Updated Merchant",
                                  "receiptDate": "2024-02-14",
                                  "amount": 199.99,
                                  "reliefCategoryId": "%s",
                                  "notes": "Updated note",
                                  "fileName": "updated.pdf",
                                  "fileUrl": "https://example.com/updated.pdf"
                                }
                                """.formatted(RELIEF_2024_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyYear", is(2024)))
                .andExpect(jsonPath("$.merchantName", is("Updated Merchant")))
                .andExpect(jsonPath("$.amount", is(199.99)))
                .andExpect(jsonPath("$.reliefCategoryId", is(RELIEF_2024_ID.toString())));

        String merchantName = jdbcTemplate.queryForObject(
                "SELECT merchant_name FROM receipts WHERE id = ?",
                String.class,
                receiptId
        );

        if (!"Updated Merchant".equals(merchantName)) {
            throw new AssertionError("Expected receipt update to persist");
        }
    }

    @Test
    void deleteReceiptRemovesReceiptForCurrentUser() throws Exception {
        UUID receiptId = UUID.fromString("77777777-7777-4777-8777-777777777788");
        insertReceipt(receiptId, devUserId, 2025, "Delete Me", "2025-08-08", "35.00", null);

        mockMvc.perform(delete("/api/receipts/{id}", receiptId))
                .andExpect(status().isNoContent());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM receipts WHERE id = ?",
                Integer.class,
                receiptId
        );

        if (!Integer.valueOf(0).equals(count)) {
            throw new AssertionError("Expected receipt to be deleted");
        }
    }

    @Test
    void cannotAccessReceiptBelongingToAnotherUser() throws Exception {
        UUID otherUserId = insertOtherUser("hidden.user@taxrelief.local");
        UUID receiptId = UUID.fromString("77777777-7777-4777-8777-777777777789");
        insertReceipt(receiptId, otherUserId, 2025, "Other Merchant", "2025-01-11", "78.00", RELIEF_2025_ID);

        mockMvc.perform(get("/api/receipts/{id}", receiptId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    void createReceiptRejectsNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyYear": 2025,
                                  "merchantName": "Invalid Receipt",
                                  "receiptDate": "2025-05-12",
                                  "amount": -1.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors[0].field", is("amount")))
                .andExpect(jsonPath("$.fieldErrors[0].message", not(is(""))));
    }

    private String receiptPayload(
            int policyYear,
            UUID reliefCategoryId,
            String merchantName,
            String receiptDate,
            String amount
    ) {
        return """
                {
                  "policyYear": %d,
                  "merchantName": "%s",
                  "receiptDate": "%s",
                  "amount": %s,
                  "reliefCategoryId": "%s",
                  "notes": "Temporary dev receipt",
                  "fileName": "receipt.pdf",
                  "fileUrl": "https://example.com/receipt.pdf"
                }
                """.formatted(policyYear, merchantName, receiptDate, amount, reliefCategoryId);
    }

    private void insertPolicyYear(UUID id, int year) {
        jdbcTemplate.update(
                "INSERT INTO policy_year (id, year, status, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                id, year, "published"
        );
    }

    private void insertRelief(
            UUID id,
            UUID policyYearId,
            String name,
            String code,
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
                2500.00,
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

    private void insertReceipt(
            UUID id,
            UUID userId,
            int policyYear,
            String merchantName,
            String receiptDate,
            String amount,
            UUID reliefCategoryId
    ) {
        UUID policyYearId = jdbcTemplate.queryForObject(
                "SELECT id FROM policy_year WHERE year = ?",
                UUID.class,
                policyYear
        );
        UUID userPolicyYearId = UUID.randomUUID();

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
                userPolicyYearId,
                userId,
                policyYearId
        );

        jdbcTemplate.update(
                """
                        INSERT INTO receipts (
                            id,
                            user_policy_year_id,
                            relief_category_id,
                            merchant_name,
                            receipt_date,
                            amount,
                            currency_code,
                            notes,
                            file_name,
                            file_url,
                            s3_bucket,
                            s3_key,
                            mime_type,
                            file_size_bytes,
                            sha256_hash,
                            status,
                            uploaded_at,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                id,
                userPolicyYearId,
                reliefCategoryId,
                merchantName,
                java.sql.Date.valueOf(receiptDate),
                amount,
                "MYR",
                "Seeded receipt",
                "seeded.pdf",
                "/api/receipts/" + id + "/file",
                "clair-tax-receipts",
                "seeded-" + id,
                "application/pdf",
                7L,
                UUID.randomUUID().toString().replace("-", ""),
                "VERIFIED"
        );
    }
}
