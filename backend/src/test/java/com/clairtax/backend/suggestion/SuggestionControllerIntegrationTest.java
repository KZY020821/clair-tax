package com.clairtax.backend.suggestion;

import com.clairtax.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuggestionControllerIntegrationTest {

    private static final UUID POLICY_YEAR_2025_ID = UUID.fromString("81111111-1111-4111-8111-111111111111");
    private static final UUID LIFESTYLE_RELIEF_ID = UUID.fromString("84444444-4444-4444-8444-444444444541");
    private static final UUID SPOUSE_RELIEF_ID = UUID.fromString("84444444-4444-4444-8444-444444444542");
    private static final UUID DISABLED_INDIVIDUAL_RELIEF_ID = UUID.fromString("84444444-4444-4444-8444-444444444543");
    private static final UUID DISABLED_SPOUSE_RELIEF_ID = UUID.fromString("84444444-4444-4444-8444-444444444544");

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
        jdbcTemplate.update("DELETE FROM tax_brackets");
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
        insertRelief(LIFESTYLE_RELIEF_ID, POLICY_YEAR_2025_ID, "Lifestyle", "lifestyle_general", "2500.00", null, 10);
        insertRelief(SPOUSE_RELIEF_ID, POLICY_YEAR_2025_ID, "Spouse relief", "spouse_relief", "4000.00", "4000.00", 20);
        insertRelief(
                DISABLED_INDIVIDUAL_RELIEF_ID,
                POLICY_YEAR_2025_ID,
                "Disabled individual relief",
                "disabled_individual",
                "6000.00",
                "6000.00",
                30
        );
        insertRelief(
                DISABLED_SPOUSE_RELIEF_ID,
                POLICY_YEAR_2025_ID,
                "Disabled spouse relief",
                "disabled_spouse",
                "5000.00",
                "5000.00",
                40
        );
    }

    @Test
    void returnsReceiptBasedSuggestionWhenVerifiedReceiptsExistWithoutCurrentClaim() throws Exception {
        UUID userPolicyYearId = insertUserPolicyYear(devUserId, POLICY_YEAR_2025_ID);
        UUID receiptId = UUID.fromString("87777777-7777-4777-8777-777777777781");
        insertVerifiedReceipt(receiptId, userPolicyYearId, LIFESTYLE_RELIEF_ID, "450.00");

        mockMvc.perform(get("/api/suggestions").param("policyYear", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyYear", is(2025)))
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.totalPotentialSaving").doesNotExist())
                .andExpect(jsonPath("$.suggestions.length()", is(1)))
                .andExpect(jsonPath("$.suggestions[0].reliefCategoryId", is(LIFESTYLE_RELIEF_ID.toString())))
                .andExpect(jsonPath("$.suggestions[0].reliefCategoryCode", is("lifestyle_general")))
                .andExpect(jsonPath("$.suggestions[0].suggestedAmount", is(450.0)))
                .andExpect(jsonPath("$.suggestions[0].currentClaimedAmount", is(0.0)))
                .andExpect(jsonPath("$.suggestions[0].reason", is("You have 1 verified receipt totaling RM 450.00 for this category")))
                .andExpect(jsonPath("$.suggestions[0].supportingReceiptIds", hasItem(receiptId.toString())))
                .andExpect(jsonPath("$.suggestions[0].priority", is("MEDIUM")))
                .andExpect(jsonPath("$.suggestions[0].suggestionType", is("RECEIPT_BASED")))
                .andExpect(jsonPath("$.suggestions[0].potentialTaxSaving").doesNotExist());
    }

    @Test
    void returnsUnderClaimedSuggestionWhenVerifiedReceiptsExceedCurrentClaim() throws Exception {
        UUID userPolicyYearId = insertUserPolicyYear(devUserId, POLICY_YEAR_2025_ID);
        insertVerifiedReceipt(
                UUID.fromString("87777777-7777-4777-8777-777777777782"),
                userPolicyYearId,
                LIFESTYLE_RELIEF_ID,
                "900.00"
        );
        insertClaim(userPolicyYearId, LIFESTYLE_RELIEF_ID, "300.00");

        mockMvc.perform(get("/api/suggestions").param("policyYear", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions.length()", is(1)))
                .andExpect(jsonPath("$.suggestions[0].suggestedAmount", is(900.0)))
                .andExpect(jsonPath("$.suggestions[0].currentClaimedAmount", is(300.0)))
                .andExpect(jsonPath("$.suggestions[0].reason", is("You have 1 verified receipt totaling RM 900.00 and can still add RM 600.00 to this claim")))
                .andExpect(jsonPath("$.suggestions[0].priority", is("MEDIUM")))
                .andExpect(jsonPath("$.suggestions[0].suggestionType", is("UNDER_CLAIMED")));
    }

    @Test
    void returnsProfileBasedSuggestionsForEligibleSpouseAndDisabilityReliefs() throws Exception {
        jdbcTemplate.update(
                """
                        UPDATE users
                        SET is_disabled = TRUE,
                            marital_status = 'married',
                            spouse_disabled = TRUE,
                            spouse_working = FALSE,
                            has_children = TRUE
                        WHERE id = ?
                        """,
                devUserId
        );

        mockMvc.perform(get("/api/suggestions").param("policyYear", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions.length()", is(3)))
                .andExpect(jsonPath("$.suggestions[*].reliefCategoryCode", containsInAnyOrder(
                        "spouse_relief",
                        "disabled_individual",
                        "disabled_spouse"
                )))
                .andExpect(jsonPath("$.suggestions[*].suggestionType", hasItem("PROFILE_BASED")))
                .andExpect(jsonPath("$.suggestions[*].priority", hasItem("HIGH")));
    }

    @Test
    void excludesProfileHiddenCategoriesFromSuggestions() throws Exception {
        mockMvc.perform(get("/api/suggestions").param("policyYear", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions.length()", is(0)));
    }

    @Test
    void returnsNotFoundForUnknownPolicyYear() throws Exception {
        mockMvc.perform(get("/api/suggestions").param("policyYear", "2099"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Policy year 2099 not found")))
                .andExpect(jsonPath("$.fieldErrors.length()", is(0)));
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
            String maxAmount,
            String unitAmount,
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
                            requires_receipt,
                            code,
                            section,
                            input_type,
                            unit_amount,
                            max_quantity,
                            display_order,
                            group_code,
                            group_max_amount,
                            exclusive_group_code,
                            requires_category_code,
                            auto_apply
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                policyYearId,
                name,
                name + " description",
                new BigDecimal(maxAmount),
                "individual",
                false,
                code,
                "family",
                unitAmount == null ? "amount" : "fixed",
                unitAmount == null ? null : new BigDecimal(unitAmount),
                null,
                displayOrder,
                null,
                null,
                null,
                null,
                false
        );
    }

    private UUID insertUserPolicyYear(UUID userId, UUID policyYearId) {
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
        return userPolicyYearId;
    }

    private void insertClaim(UUID userPolicyYearId, UUID reliefCategoryId, String claimedAmount) {
        jdbcTemplate.update(
                """
                        INSERT INTO user_relief_claims (
                            id,
                            user_policy_year_id,
                            relief_category_id,
                            claimed_amount
                        ) VALUES (?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                userPolicyYearId,
                reliefCategoryId,
                new BigDecimal(claimedAmount)
        );
    }

    private void insertVerifiedReceipt(UUID receiptId, UUID userPolicyYearId, UUID reliefCategoryId, String amount) {
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
                receiptId,
                userPolicyYearId,
                reliefCategoryId,
                "Seeded merchant",
                java.sql.Date.valueOf("2025-03-10"),
                new BigDecimal(amount),
                "MYR",
                "Seeded receipt",
                "seeded.pdf",
                "/api/receipts/" + receiptId + "/file",
                "clair-tax-receipts",
                "seeded-" + receiptId,
                "application/pdf",
                7L,
                UUID.randomUUID().toString().replace("-", ""),
                "VERIFIED"
        );
    }
}
