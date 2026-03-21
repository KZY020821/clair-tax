package com.clairtax.backend.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicyControllerIntegrationTest {

    private static final UUID POLICY_YEAR_2025_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID POLICY_YEAR_2026_ID = UUID.fromString("11111111-1111-4111-8111-111111111112");
    private static final UUID SELF_RELIEF_2025_ID = UUID.fromString("44444444-4444-4444-8444-444444444441");
    private static final UUID LIFESTYLE_RELIEF_2025_ID = UUID.fromString("44444444-4444-4444-8444-444444444442");
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
                DIGITAL_LEARNING_2026_ID,
                POLICY_YEAR_2026_ID,
                "Digital Learning",
                "Draft relief for work and study-related devices.",
                "3000.00",
                true
        );
    }

    @Test
    void returnsPolicyYearWithReliefCategories() throws Exception {
        mockMvc.perform(get("/api/policies/2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(POLICY_YEAR_2025_ID.toString()))
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.reliefCategories", hasSize(2)))
                .andExpect(jsonPath("$.reliefCategories[0].id").value(LIFESTYLE_RELIEF_2025_ID.toString()))
                .andExpect(jsonPath("$.reliefCategories[0].name").value("Lifestyle"))
                .andExpect(jsonPath("$.reliefCategories[0].description").value("Books, devices, sports equipment, and internet subscriptions."))
                .andExpect(jsonPath("$.reliefCategories[0].maxAmount").value(2500.00))
                .andExpect(jsonPath("$.reliefCategories[0].requiresReceipt").value(true))
                .andExpect(jsonPath("$.reliefCategories[1].id").value(SELF_RELIEF_2025_ID.toString()))
                .andExpect(jsonPath("$.reliefCategories[1].name").value("Self and Dependent"))
                .andExpect(jsonPath("$.reliefCategories[1].maxAmount").value(9000.00))
                .andExpect(jsonPath("$.reliefCategories[1].requiresReceipt").value(false));
    }

    @Test
    void returnsNotFoundWhenPolicyYearIsMissing() throws Exception {
        mockMvc.perform(get("/api/policies/2030"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Policy year 2030 not found"));
    }
}
