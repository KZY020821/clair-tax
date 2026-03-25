package com.clairtax.backend.user;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileControllerIntegrationTest {

    private static final UUID POLICY_YEAR_2025_ID = UUID.fromString("71111111-1111-4111-8111-111111111111");
    private static final UUID RECEIPT_RELIEF_ID = UUID.fromString("74444444-4444-4444-8444-444444444541");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Value("${clair.dev-user.email}")
    private String devUserEmail;

    @Value("${clair.receipts.storage-path}")
    private String receiptStoragePath;

    private UUID devUserId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM user_relief_claims");
        jdbcTemplate.update("DELETE FROM receipts");
        jdbcTemplate.update("DELETE FROM user_policy_years");
        jdbcTemplate.update("DELETE FROM relief_categories");
        jdbcTemplate.update("DELETE FROM tax_brackets");
        jdbcTemplate.update("DELETE FROM policy_year");
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

        Path storageDirectory = Path.of(receiptStoragePath);
        if (Files.exists(storageDirectory)) {
            try (var files = Files.list(storageDirectory)) {
                files.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }

        devUserId = appUserRepository.findByEmail(devUserEmail)
                .orElseThrow(() -> new AssertionError("Expected bootstrapped dev user to exist"))
                .getId();
    }

    @Test
    void getProfileReturnsBootstrappedDefaultProfile() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(devUserEmail)))
                .andExpect(jsonPath("$.isDisabled", is(false)))
                .andExpect(jsonPath("$.maritalStatus", is("single")))
                .andExpect(jsonPath("$.spouseDisabled").isEmpty())
                .andExpect(jsonPath("$.spouseWorking").isEmpty())
                .andExpect(jsonPath("$.hasChildren").isEmpty());
    }

    @Test
    void updateProfilePersistsSavedFieldsAndClearsInvalidSpouseFields() throws Exception {
        mockMvc.perform(put("/api/profile")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "isDisabled": true,
                                  "maritalStatus": "married",
                                  "spouseDisabled": true,
                                  "spouseWorking": false,
                                  "hasChildren": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDisabled", is(true)))
                .andExpect(jsonPath("$.maritalStatus", is("married")))
                .andExpect(jsonPath("$.spouseDisabled", is(true)))
                .andExpect(jsonPath("$.spouseWorking", is(false)))
                .andExpect(jsonPath("$.hasChildren", is(true)));

        mockMvc.perform(put("/api/profile")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "isDisabled": false,
                                  "maritalStatus": "previously_married",
                                  "spouseDisabled": true,
                                  "spouseWorking": true,
                                  "hasChildren": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDisabled", is(false)))
                .andExpect(jsonPath("$.maritalStatus", is("previously_married")))
                .andExpect(jsonPath("$.spouseDisabled").isEmpty())
                .andExpect(jsonPath("$.spouseWorking").isEmpty())
                .andExpect(jsonPath("$.hasChildren", is(true)));
    }

    @Test
    void resetAccountDeletesUserDataAndResetsProfile() throws Exception {
        insertPolicyYear(POLICY_YEAR_2025_ID, 2025);
        insertRelief(RECEIPT_RELIEF_ID, POLICY_YEAR_2025_ID);

        mockMvc.perform(put("/api/profile")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "isDisabled": true,
                                  "maritalStatus": "married",
                                  "spouseDisabled": true,
                                  "spouseWorking": false,
                                  "hasChildren": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/user-years")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "policyYear": 2025
                                }
                                """))
                .andExpect(status().isOk());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "receipt".getBytes()
        );

        mockMvc.perform(multipart("/api/user-years/{year}/receipts", 2025)
                        .file(file)
                        .param("merchantName", "Clinic")
                        .param("receiptDate", "2025-06-12")
                        .param("amount", "120.50")
                        .param("reliefCategoryId", RECEIPT_RELIEF_ID.toString()))
                .andExpect(status().isCreated());

        UUID storedReceiptId = jdbcTemplate.queryForObject(
                "SELECT id FROM receipts WHERE merchant_name = ?",
                UUID.class,
                "Clinic"
        );
        Path storedReceipt = Path.of(receiptStoragePath).resolve(storedReceiptId.toString());
        if (!Files.exists(storedReceipt)) {
            throw new AssertionError("Expected uploaded receipt file to be stored");
        }

        mockMvc.perform(delete("/api/profile/account"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(devUserEmail)))
                .andExpect(jsonPath("$.isDisabled", is(false)))
                .andExpect(jsonPath("$.maritalStatus", is("single")))
                .andExpect(jsonPath("$.spouseDisabled").isEmpty())
                .andExpect(jsonPath("$.spouseWorking").isEmpty())
                .andExpect(jsonPath("$.hasChildren").isEmpty());

        mockMvc.perform(get("/api/user-years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));

        mockMvc.perform(get("/api/receipts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));

        if (Files.exists(storedReceipt)) {
            throw new AssertionError("Expected reset to remove stored receipt files");
        }

        Integer workspaceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_policy_years WHERE user_id = ?",
                Integer.class,
                devUserId
        );

        if (!Integer.valueOf(0).equals(workspaceCount)) {
            throw new AssertionError("Expected reset to delete user year workspaces");
        }
    }

    private void insertPolicyYear(UUID id, int year) {
        jdbcTemplate.update(
                "INSERT INTO policy_year (id, year, status, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                id,
                year,
                "published"
        );
    }

    private void insertRelief(UUID id, UUID policyYearId) {
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
                "Lifestyle",
                "Lifestyle description",
                2500.00,
                "lifestyle",
                "lifestyle_general",
                "lifestyle",
                "amount",
                10,
                false,
                true
        );
    }
}
