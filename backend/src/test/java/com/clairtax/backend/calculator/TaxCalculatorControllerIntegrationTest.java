package com.clairtax.backend.calculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaxCalculatorControllerIntegrationTest {

    private static final UUID POLICY_YEAR_2025_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID POLICY_YEAR_2026_ID = UUID.fromString("11111111-1111-4111-8111-111111111112");
    private static final UUID SELF_RELIEF_ID = UUID.fromString("44444444-4444-4444-8444-444444444501");
    private static final UUID SPOUSE_RELIEF_ID = UUID.fromString("44444444-4444-4444-8444-444444444502");
    private static final UUID CHILD_RELIEF_ID = UUID.fromString("44444444-4444-4444-8444-444444444503");
    private static final UUID LIFESTYLE_RELIEF_ID = UUID.fromString("44444444-4444-4444-8444-444444444504");
    private static final UUID EPF_RELIEF_ID = UUID.fromString("44444444-4444-4444-8444-444444444505");
    private static final UUID MEDICAL_RELIEF_ID = UUID.fromString("44444444-4444-4444-8444-444444444506");
    private static final UUID SCREENING_RELIEF_ID = UUID.fromString("44444444-4444-4444-8444-444444444507");
    private static final UUID CHILD_SUPPORT_RELIEF_ID = UUID.fromString("44444444-4444-4444-8444-444444444508");
    private static final UUID DISABLED_SPOUSE_RELIEF_ID = UUID.fromString("44444444-4444-4444-8444-444444444509");
    private static final UUID HOME_LOAN_SMALL_ID = UUID.fromString("44444444-4444-4444-8444-444444444510");
    private static final UUID HOME_LOAN_MEDIUM_ID = UUID.fromString("44444444-4444-4444-8444-444444444511");
    private static final UUID DISABLED_CHILD_HIGHER_EDUCATION_ID = UUID.fromString("44444444-4444-4444-8444-444444444512");
    private static final UUID DRAFT_RELIEF_2026_ID = UUID.fromString("44444444-4444-4444-8444-444444444599");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM relief_categories");
        jdbcTemplate.update("DELETE FROM tax_brackets");
        jdbcTemplate.update("DELETE FROM policy_year");

        jdbcTemplate.update(
                "INSERT INTO policy_year (id, year, status, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                POLICY_YEAR_2025_ID, 2025, "published"
        );
        jdbcTemplate.update(
                "INSERT INTO policy_year (id, year, status, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                POLICY_YEAR_2026_ID, 2026, "draft"
        );

        insertBracket(UUID.fromString("33333333-3333-4333-8333-333333333331"), POLICY_YEAR_2025_ID, "0.00", "5000.00", "0.00");
        insertBracket(UUID.fromString("33333333-3333-4333-8333-333333333332"), POLICY_YEAR_2025_ID, "5000.00", "20000.00", "1.00");
        insertBracket(UUID.fromString("33333333-3333-4333-8333-333333333333"), POLICY_YEAR_2025_ID, "20000.00", "35000.00", "3.00");
        insertBracket(UUID.fromString("33333333-3333-4333-8333-333333333334"), POLICY_YEAR_2025_ID, "35000.00", "50000.00", "6.00");
        insertBracket(UUID.fromString("33333333-3333-4333-8333-333333333335"), POLICY_YEAR_2025_ID, "50000.00", "70000.00", "11.00");
        insertBracket(UUID.fromString("33333333-3333-4333-8333-333333333336"), POLICY_YEAR_2025_ID, "70000.00", null, "19.00");
        insertBracket(UUID.fromString("33333333-3333-4333-8333-333333333337"), POLICY_YEAR_2026_ID, "0.00", "60000.00", "1.00");
        insertBracket(UUID.fromString("33333333-3333-4333-8333-333333333338"), POLICY_YEAR_2026_ID, "60000.00", null, "4.00");

        insertRelief(
                SELF_RELIEF_ID,
                POLICY_YEAR_2025_ID,
                "Individual and dependent relatives",
                "Automatic resident self relief.",
                "9000.00",
                "individual",
                false,
                "self_and_dependents",
                "identity",
                "fixed",
                "9000.00",
                null,
                10,
                null,
                null,
                null,
                null,
                true
        );
        insertRelief(
                SPOUSE_RELIEF_ID,
                POLICY_YEAR_2025_ID,
                "Spouse relief",
                "Fixed spouse relief.",
                "4000.00",
                "family",
                false,
                "spouse_relief",
                "family",
                "fixed",
                "4000.00",
                null,
                20,
                "spouse_relief_cap",
                "4000.00",
                null,
                null,
                false
        );
        insertRelief(
                CHILD_RELIEF_ID,
                POLICY_YEAR_2025_ID,
                "Child below 18 years old",
                "Relief per child below 18.",
                "2000.00",
                "family",
                false,
                "child_below_18",
                "family",
                "count",
                "2000.00",
                null,
                30,
                null,
                null,
                null,
                null,
                false
        );
        insertRelief(
                LIFESTYLE_RELIEF_ID,
                POLICY_YEAR_2025_ID,
                "Lifestyle",
                "Lifestyle relief.",
                "2500.00",
                "lifestyle",
                true,
                "lifestyle_general",
                "lifestyle",
                "amount",
                null,
                null,
                40,
                null,
                null,
                null,
                null,
                false
        );
        insertRelief(
                EPF_RELIEF_ID,
                POLICY_YEAR_2025_ID,
                "EPF contribution",
                "EPF contribution cap.",
                "4000.00",
                "individual",
                false,
                "epf_contribution",
                "retirement",
                "amount",
                null,
                null,
                50,
                null,
                null,
                null,
                null,
                false
        );
        insertRelief(
                MEDICAL_RELIEF_ID,
                POLICY_YEAR_2025_ID,
                "Medical treatment for self, spouse or child",
                "Medical relief group item.",
                "10000.00",
                "individual",
                true,
                "medical_treatment_self_family",
                "medical",
                "amount",
                null,
                null,
                60,
                "medical_total",
                "10000.00",
                null,
                null,
                false
        );
        insertRelief(
                SCREENING_RELIEF_ID,
                POLICY_YEAR_2025_ID,
                "Health screening and mental health consultation",
                "Medical relief group item.",
                "1000.00",
                "individual",
                true,
                "health_screening_and_mental_health",
                "medical",
                "amount",
                null,
                null,
                70,
                "medical_total",
                "10000.00",
                null,
                null,
                false
        );
        insertRelief(
                CHILD_SUPPORT_RELIEF_ID,
                POLICY_YEAR_2025_ID,
                "Child learning disability diagnosis and intervention",
                "Medical relief group item.",
                "6000.00",
                "family",
                true,
                "child_learning_disability_support",
                "medical",
                "amount",
                null,
                null,
                80,
                "medical_total",
                "10000.00",
                null,
                null,
                false
        );
        insertRelief(
                DISABLED_SPOUSE_RELIEF_ID,
                POLICY_YEAR_2025_ID,
                "Disabled spouse additional relief",
                "Additional spouse relief.",
                "6000.00",
                "family",
                false,
                "disabled_spouse",
                "family",
                "fixed",
                "6000.00",
                null,
                90,
                null,
                null,
                null,
                null,
                false
        );
        insertRelief(
                HOME_LOAN_SMALL_ID,
                POLICY_YEAR_2025_ID,
                "First home loan interest up to RM500,000",
                "Mutually exclusive home loan option.",
                "7000.00",
                "individual",
                false,
                "first_home_loan_interest_upto_500k",
                "property",
                "amount",
                null,
                null,
                100,
                null,
                null,
                "home_loan_interest",
                null,
                false
        );
        insertRelief(
                HOME_LOAN_MEDIUM_ID,
                POLICY_YEAR_2025_ID,
                "First home loan interest RM500,001 to RM750,000",
                "Mutually exclusive home loan option.",
                "5000.00",
                "individual",
                false,
                "first_home_loan_interest_500k_to_750k",
                "property",
                "amount",
                null,
                null,
                110,
                null,
                null,
                "home_loan_interest",
                null,
                false
        );
        insertRelief(
                DISABLED_CHILD_HIGHER_EDUCATION_ID,
                POLICY_YEAR_2025_ID,
                "Disabled child in diploma or higher education",
                "Relief per disabled child in higher education.",
                "8000.00",
                "family",
                false,
                "disabled_child_higher_education",
                "family",
                "count",
                "8000.00",
                null,
                120,
                null,
                null,
                null,
                null,
                false
        );
        insertRelief(
                DRAFT_RELIEF_2026_ID,
                POLICY_YEAR_2026_ID,
                "Draft digital learning",
                "Draft relief for validation.",
                "3000.00",
                "individual",
                true,
                "draft_digital_learning",
                "education",
                "amount",
                null,
                null,
                10,
                null,
                null,
                null,
                null,
                false
        );
    }

    @Test
    void calculatesTaxUsingYa2025RulesAndSummaryFields() throws Exception {
        String request = """
                {
                  "policyYear": 2025,
                  "grossIncome": 60000.00,
                  "zakat": 200.00,
                  "selectedReliefs": [
                    {
                      "reliefCategoryId": "%s",
                      "selected": true
                    },
                    {
                      "reliefCategoryId": "%s",
                      "quantity": 1
                    },
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 1800.00
                    },
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 4000.00
                    }
                  ]
                }
                """.formatted(SPOUSE_RELIEF_ID, CHILD_RELIEF_ID, LIFESTYLE_RELIEF_ID, EPF_RELIEF_ID);

        mockMvc.perform(post("/api/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyYear").value(2025))
                .andExpect(jsonPath("$.grossIncome").value(60000.00))
                .andExpect(jsonPath("$.totalRelief").value(20800.00))
                .andExpect(jsonPath("$.taxableIncome").value(39200.00))
                .andExpect(jsonPath("$.chargeableIncome").value(39200.00))
                .andExpect(jsonPath("$.taxAmount").value(852.00))
                .andExpect(jsonPath("$.taxRebate").value(0.00))
                .andExpect(jsonPath("$.zakat").value(200.00))
                .andExpect(jsonPath("$.taxYouShouldPay").value(652.00))
                .andExpect(jsonPath("$.totalTaxPayable").value(652.00))
                .andExpect(jsonPath("$.taxBreakdown[0].taxableAmount").value(5000.00))
                .andExpect(jsonPath("$.taxBreakdown[1].taxableAmount").value(15000.00))
                .andExpect(jsonPath("$.taxBreakdown[2].taxableAmount").value(15000.00))
                .andExpect(jsonPath("$.taxBreakdown[3].taxableAmount").value(4200.00))
                .andExpect(jsonPath("$.taxBreakdown[3].taxForBracket").value(252.00));
    }

    @Test
    void capsSharedMedicalReliefsAndAppliesAutomaticRebate() throws Exception {
        String request = """
                {
                  "policyYear": 2025,
                  "grossIncome": 50000.00,
                  "selectedReliefs": [
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 9000.00
                    },
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 1000.00
                    },
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 6000.00
                    }
                  ]
                }
                """.formatted(MEDICAL_RELIEF_ID, SCREENING_RELIEF_ID, CHILD_SUPPORT_RELIEF_ID);

        mockMvc.perform(post("/api/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRelief").value(19000.00))
                .andExpect(jsonPath("$.taxableIncome").value(31000.00))
                .andExpect(jsonPath("$.taxAmount").value(480.00))
                .andExpect(jsonPath("$.taxRebate").value(400.00))
                .andExpect(jsonPath("$.taxYouShouldPay").value(80.00));
    }

    @Test
    void rejectsExclusiveHomeLoanSelections() throws Exception {
        String request = """
                {
                  "policyYear": 2025,
                  "grossIncome": 90000.00,
                  "selectedReliefs": [
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 5000.00
                    },
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 4000.00
                    }
                  ]
                }
                """.formatted(HOME_LOAN_SMALL_ID, HOME_LOAN_MEDIUM_ID);

        mockMvc.perform(post("/api/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("home_loan_interest")));
    }

    @Test
    void allowsStandaloneDisabledSpouseRelief() throws Exception {
        String request = """
                {
                  "policyYear": 2025,
                  "grossIncome": 70000.00,
                  "selectedReliefs": [
                    {
                      "reliefCategoryId": "%s",
                      "selected": true
                    }
                  ]
                }
                """.formatted(DISABLED_SPOUSE_RELIEF_ID);

        mockMvc.perform(post("/api/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRelief").value(15000.00))
                .andExpect(jsonPath("$.chargeableIncome").value(55000.00));
    }

    @Test
    void calculatesDisabledChildHigherEducationPerChildWithoutQuantityLimit() throws Exception {
        String request = """
                {
                  "policyYear": 2025,
                  "grossIncome": 80000.00,
                  "selectedReliefs": [
                    {
                      "reliefCategoryId": "%s",
                      "quantity": 3
                    }
                  ]
                }
                """.formatted(DISABLED_CHILD_HIGHER_EDUCATION_ID);

        mockMvc.perform(post("/api/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRelief").value(33000.00))
                .andExpect(jsonPath("$.chargeableIncome").value(47000.00));
    }

    @Test
    void rejectsReliefCategoriesOutsideSelectedPolicyYear() throws Exception {
        String request = """
                {
                  "policyYear": 2025,
                  "grossIncome": 50000.00,
                  "selectedReliefs": [
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 1000.00
                    }
                  ]
                }
                """.formatted(DRAFT_RELIEF_2026_ID);

        mockMvc.perform(post("/api/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("does not belong to policy year 2025")));
    }

    @Test
    void allowsFrontendOriginsForCalculatorRequests() throws Exception {
        mockMvc.perform(options("/api/calculator/calculate")
                        .header(ORIGIN, "http://127.0.0.1:3000")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:3000"))
                .andExpect(header().stringValues("Vary", hasItem("Origin")));
    }

    private void insertBracket(
            UUID id,
            UUID policyYearId,
            String minIncome,
            String maxIncome,
            String taxRate
    ) {
        jdbcTemplate.update(
                "INSERT INTO tax_brackets (id, policy_year_id, min_income, max_income, tax_rate) VALUES (?, ?, ?, ?, ?)",
                id,
                policyYearId,
                minIncome,
                maxIncome,
                taxRate
        );
    }

    private void insertRelief(
            UUID id,
            UUID policyYearId,
            String name,
            String description,
            String maxAmount,
            String type,
            boolean requiresReceipt,
            String code,
            String section,
            String inputType,
            String unitAmount,
            Integer maxQuantity,
            int displayOrder,
            String groupCode,
            String groupMaxAmount,
            String exclusiveGroupCode,
            String requiresCategoryCode,
            boolean autoApply
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
                description,
                maxAmount,
                type,
                requiresReceipt,
                code,
                section,
                inputType,
                unitAmount,
                maxQuantity,
                displayOrder,
                groupCode,
                groupMaxAmount,
                exclusiveGroupCode,
                requiresCategoryCode,
                autoApply
        );
    }
}
