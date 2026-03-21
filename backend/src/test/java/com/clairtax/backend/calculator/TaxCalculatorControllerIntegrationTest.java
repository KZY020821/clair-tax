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
    private static final UUID SELF_RELIEF_2025_ID = UUID.fromString("44444444-4444-4444-8444-444444444441");
    private static final UUID LIFESTYLE_RELIEF_2025_ID = UUID.fromString("44444444-4444-4444-8444-444444444442");
    private static final UUID MEDICAL_RELIEF_2025_ID = UUID.fromString("44444444-4444-4444-8444-444444444443");
    private static final UUID DIGITAL_LEARNING_2026_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

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

        jdbcTemplate.update(
                "INSERT INTO tax_brackets (id, policy_year_id, min_income, max_income, tax_rate) VALUES (?, ?, ?, ?, ?)",
                UUID.fromString("33333333-3333-4333-8333-333333333331"),
                POLICY_YEAR_2025_ID,
                "0.00",
                "50000.00",
                "1.00"
        );
        jdbcTemplate.update(
                "INSERT INTO tax_brackets (id, policy_year_id, min_income, max_income, tax_rate) VALUES (?, ?, ?, ?, ?)",
                UUID.fromString("33333333-3333-4333-8333-333333333332"),
                POLICY_YEAR_2025_ID,
                "50000.01",
                "100000.00",
                "3.00"
        );
        jdbcTemplate.update(
                "INSERT INTO tax_brackets (id, policy_year_id, min_income, max_income, tax_rate) VALUES (?, ?, ?, ?, ?)",
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                POLICY_YEAR_2025_ID,
                "100000.01",
                null,
                "5.00"
        );
        jdbcTemplate.update(
                "INSERT INTO tax_brackets (id, policy_year_id, min_income, max_income, tax_rate) VALUES (?, ?, ?, ?, ?)",
                UUID.fromString("33333333-3333-4333-8333-333333333334"),
                POLICY_YEAR_2026_ID,
                "0.00",
                "60000.00",
                "1.00"
        );
        jdbcTemplate.update(
                "INSERT INTO tax_brackets (id, policy_year_id, min_income, max_income, tax_rate) VALUES (?, ?, ?, ?, ?)",
                UUID.fromString("33333333-3333-4333-8333-333333333335"),
                POLICY_YEAR_2026_ID,
                "60000.01",
                null,
                "4.00"
        );

        jdbcTemplate.update(
                """
                INSERT INTO relief_categories
                    (id, policy_year_id, name, description, max_amount, requires_receipt)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                SELF_RELIEF_2025_ID,
                POLICY_YEAR_2025_ID,
                "Self and Dependent",
                "Baseline relief for the taxpayer and dependents.",
                "9000.00",
                false
        );
        jdbcTemplate.update(
                """
                INSERT INTO relief_categories
                    (id, policy_year_id, name, description, max_amount, requires_receipt)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                LIFESTYLE_RELIEF_2025_ID,
                POLICY_YEAR_2025_ID,
                "Lifestyle",
                "Books, devices, sports equipment, and internet subscriptions.",
                "2500.00",
                true
        );
        jdbcTemplate.update(
                """
                INSERT INTO relief_categories
                    (id, policy_year_id, name, description, max_amount, requires_receipt)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                MEDICAL_RELIEF_2025_ID,
                POLICY_YEAR_2025_ID,
                "Medical Expenses for Parents",
                "Eligible medical support and treatment for parents.",
                "8000.00",
                true
        );
        jdbcTemplate.update(
                """
                INSERT INTO relief_categories
                    (id, policy_year_id, name, description, max_amount, requires_receipt)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                DIGITAL_LEARNING_2026_ID,
                POLICY_YEAR_2026_ID,
                "Digital Learning",
                "Draft relief for work and study-related devices.",
                "3000.00",
                true
        );
    }

    @Test
    void calculatesTaxFromDbBackedRules() throws Exception {
        String request = """
                {
                  "policyYear": 2025,
                  "grossIncome": 85000.00,
                  "selectedReliefs": [
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 4000.00
                    },
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 1800.00
                    },
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 1200.00
                    }
                  ]
                }
                """.formatted(SELF_RELIEF_2025_ID, LIFESTYLE_RELIEF_2025_ID, MEDICAL_RELIEF_2025_ID);

        mockMvc.perform(post("/api/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyYear").value(2025))
                .andExpect(jsonPath("$.grossIncome").value(85000.00))
                .andExpect(jsonPath("$.totalRelief").value(7000.00))
                .andExpect(jsonPath("$.chargeableIncome").value(78000.00))
                .andExpect(jsonPath("$.taxBreakdown[0].taxableAmount").value(50000.00))
                .andExpect(jsonPath("$.taxBreakdown[0].taxForBracket").value(500.00))
                .andExpect(jsonPath("$.taxBreakdown[1].taxableAmount").value(28000.00))
                .andExpect(jsonPath("$.taxBreakdown[1].taxForBracket").value(840.00))
                .andExpect(jsonPath("$.taxBreakdown[2].taxableAmount").value(0.00))
                .andExpect(jsonPath("$.totalTaxPayable").value(1340.00));
    }

    @Test
    void capsReliefClaimsAtCategoryMaximum() throws Exception {
        String request = """
                {
                  "policyYear": 2025,
                  "grossIncome": 100000.00,
                  "selectedReliefs": [
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 12000.00
                    }
                  ]
                }
                """.formatted(SELF_RELIEF_2025_ID);

        mockMvc.perform(post("/api/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRelief").value(9000.00))
                .andExpect(jsonPath("$.chargeableIncome").value(91000.00))
                .andExpect(jsonPath("$.totalTaxPayable").value(1730.00));
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
                """.formatted(DIGITAL_LEARNING_2026_ID);

        mockMvc.perform(post("/api/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("do not belong to policy year 2025")));
    }

    @Test
    void rejectsNegativeValues() throws Exception {
        String request = """
                {
                  "policyYear": 2025,
                  "grossIncome": -1.00,
                  "selectedReliefs": [
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": -25.00
                    }
                  ]
                }
                """.formatted(SELF_RELIEF_2025_ID);

        mockMvc.perform(post("/api/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("grossIncome")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("selectedReliefs[0].claimedAmount")));
    }

    @Test
    void neverReturnsNegativeChargeableIncome() throws Exception {
        String request = """
                {
                  "policyYear": 2025,
                  "grossIncome": 1000.00,
                  "selectedReliefs": [
                    {
                      "reliefCategoryId": "%s",
                      "claimedAmount": 9000.00
                    }
                  ]
                }
                """.formatted(SELF_RELIEF_2025_ID);

        mockMvc.perform(post("/api/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRelief").value(9000.00))
                .andExpect(jsonPath("$.chargeableIncome").value(0.00))
                .andExpect(jsonPath("$.totalTaxPayable").value(0.00))
                .andExpect(jsonPath("$.taxBreakdown[0].taxableAmount").value(0.00))
                .andExpect(jsonPath("$.taxBreakdown[1].taxableAmount").value(0.00))
                .andExpect(jsonPath("$.taxBreakdown[2].taxableAmount").value(0.00));
    }

    @Test
    void returnsClearErrorWhenPolicyYearDoesNotExist() throws Exception {
        String request = """
                {
                  "policyYear": 2030,
                  "grossIncome": 50000.00,
                  "selectedReliefs": []
                }
                """;

        mockMvc.perform(post("/api/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Policy year 2030 not found"));
    }

    @Test
    void allowsCorsPreflightForCalculatorPostRequests() throws Exception {
        mockMvc.perform(options("/api/calculator/calculate")
                        .header(ORIGIN, "http://localhost:3000")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }
}
