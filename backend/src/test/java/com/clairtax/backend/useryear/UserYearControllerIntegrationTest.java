package com.clairtax.backend.useryear;

import com.clairtax.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Value("${clair.dev-user.email}")
    private String devUserEmail;

    private UUID devUserId;

    @BeforeEach
    void setUp() {
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
    void workspaceShowsCategorySummaryAndUploadRefreshesTotals() throws Exception {
        insertUserPolicyYear(devUserId, POLICY_YEAR_2025_ID);

        mockMvc.perform(get("/api/user-years/2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year", is(2025)))
                .andExpect(jsonPath("$.totalClaimedAmount", is(0)))
                .andExpect(jsonPath("$.totalReceiptCount", is(0)))
                .andExpect(jsonPath("$.categories.length()", is(2)))
                .andExpect(jsonPath("$.categories[0].reliefCategoryId", is(LIFESTYLE_RELIEF_ID.toString())))
                .andExpect(jsonPath("$.categories[0].claimedAmount", is(0)))
                .andExpect(jsonPath("$.categories[0].remainingAmount", is(2500.00)))
                .andExpect(jsonPath("$.categories[0].receiptCount", is(0)));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "clinic.pdf",
                "application/pdf",
                "receipt".getBytes()
        );

        mockMvc.perform(multipart("/api/user-years/{year}/receipts", 2025)
                        .file(file)
                        .param("merchantName", "Clinic")
                        .param("receiptDate", "2025-06-12")
                        .param("amount", "120.50")
                        .param("reliefCategoryId", LIFESTYLE_RELIEF_ID.toString())
                        .param("notes", "Follow-up visit"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyYear", is(2025)))
                .andExpect(jsonPath("$.merchantName", is("Clinic")))
                .andExpect(jsonPath("$.amount", is(120.50)))
                .andExpect(jsonPath("$.fileName", is("clinic.pdf")))
                .andExpect(jsonPath("$.fileUrl", containsString("/api/receipts/")));

        mockMvc.perform(get("/api/user-years/2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClaimedAmount", is(120.50)))
                .andExpect(jsonPath("$.totalReceiptCount", is(1)))
                .andExpect(jsonPath("$.categories[0].claimedAmount", is(120.50)))
                .andExpect(jsonPath("$.categories[0].remainingAmount", is(2379.50)))
                .andExpect(jsonPath("$.categories[0].receiptCount", is(1)));

        mockMvc.perform(get("/api/user-years/2025/receipts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].merchantName", is("Clinic")));
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
