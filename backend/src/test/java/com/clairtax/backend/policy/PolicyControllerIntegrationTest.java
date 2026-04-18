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
    private static final UUID SELF_RELIEF_ID = UUID.fromString("44444444-4444-4444-8444-444444444441");
    private static final UUID SPOUSE_RELIEF_ID = UUID.fromString("44444444-4444-4444-8444-444444444442");
    private static final UUID CHILD_RELIEF_ID = UUID.fromString("44444444-4444-4444-8444-444444444443");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_relief_claims");
        jdbcTemplate.update("DELETE FROM receipts");
        jdbcTemplate.update("DELETE FROM user_policy_years");
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
    }

    @Test
    void returnsPolicyYearWithExtendedReliefMetadata() throws Exception {
        mockMvc.perform(get("/api/policies/2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(POLICY_YEAR_2025_ID.toString()))
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.reliefCategories", hasSize(3)))
                .andExpect(jsonPath("$.reliefCategories[0].id").value(SELF_RELIEF_ID.toString()))
                .andExpect(jsonPath("$.reliefCategories[0].code").value("self_and_dependents"))
                .andExpect(jsonPath("$.reliefCategories[0].name").value("Individual and dependent relatives"))
                .andExpect(jsonPath("$.reliefCategories[0].section").value("identity"))
                .andExpect(jsonPath("$.reliefCategories[0].inputType").value("fixed"))
                .andExpect(jsonPath("$.reliefCategories[0].unitAmount").value(9000.00))
                .andExpect(jsonPath("$.reliefCategories[0].maxQuantity").isEmpty())
                .andExpect(jsonPath("$.reliefCategories[0].displayOrder").value(10))
                .andExpect(jsonPath("$.reliefCategories[0].autoApply").value(true))
                .andExpect(jsonPath("$.reliefCategories[0].requiresReceipt").value(false))
                .andExpect(jsonPath("$.reliefCategories[1].id").value(SPOUSE_RELIEF_ID.toString()))
                .andExpect(jsonPath("$.reliefCategories[1].groupCode").value("spouse_relief_cap"))
                .andExpect(jsonPath("$.reliefCategories[1].groupMaxAmount").value(4000.00))
                .andExpect(jsonPath("$.reliefCategories[1].autoApply").value(false))
                .andExpect(jsonPath("$.reliefCategories[2].id").value(CHILD_RELIEF_ID.toString()))
                .andExpect(jsonPath("$.reliefCategories[2].inputType").value("count"))
                .andExpect(jsonPath("$.reliefCategories[2].maxQuantity").isEmpty());
    }

    @Test
    void returnsNotFoundWhenPolicyYearIsMissing() throws Exception {
        mockMvc.perform(get("/api/policies/2030"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Policy year 2030 not found"));
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
