package com.clairtax.backend.user.service;

import com.clairtax.backend.calculator.exception.ResourceNotFoundException;
import com.clairtax.backend.receipt.entity.Receipt;
import com.clairtax.backend.receipt.repository.ReceiptRepository;
import com.clairtax.backend.receipt.storage.ReceiptObjectStorageService;
import com.clairtax.backend.user.dto.ProfileResponse;
import com.clairtax.backend.user.dto.UpdateProfileRequest;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class ProfileService {

    private final AppUserRepository appUserRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ReceiptRepository receiptRepository;
    private final ReceiptObjectStorageService receiptStorageService;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public ProfileService(
            AppUserRepository appUserRepository,
            CurrentUserProvider currentUserProvider,
            ReceiptRepository receiptRepository,
            ReceiptObjectStorageService receiptStorageService,
            JdbcTemplate jdbcTemplate,
            DataSource dataSource
    ) {
        this.appUserRepository = appUserRepository;
        this.currentUserProvider = currentUserProvider;
        this.receiptRepository = receiptRepository;
        this.receiptStorageService = receiptStorageService;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile() {
        return toResponse(getCurrentUserEntity());
    }

    public ProfileResponse updateProfile(UpdateProfileRequest request) {
        AppUser user = getCurrentUserEntity();
        user.updateProfile(
                request.isDisabled(),
                request.maritalStatus(),
                request.spouseDisabled(),
                request.spouseWorking(),
                request.hasChildren()
        );

        return toResponse(user);
    }

    public void resetAccount() {
        AppUser user = getCurrentUserEntity();
        List<String> storedReceiptIds = receiptRepository.findAllDetailedByUserId(user.getId())
                .stream()
                .map(Receipt::getS3Key)
                .toList();

        deleteUserOwnedRowsIfPresent("ai_suggestions", user.getId());
        deleteUserOwnedRowsIfPresent("audit_logs", user.getId());
        deleteUserOwnedRowsIfPresent("user_tax_profile", user.getId());
        jdbcTemplate.update(
                """
                        DELETE FROM receipt_review_actions
                        WHERE receipt_id IN (
                            SELECT id
                            FROM receipts
                            WHERE user_policy_year_id IN (
                                SELECT id
                                FROM user_policy_years
                                WHERE user_id = ?
                            )
                        )
                        """,
                user.getId()
        );
        jdbcTemplate.update(
                """
                        DELETE FROM receipt_extraction_results
                        WHERE receipt_id IN (
                            SELECT id
                            FROM receipts
                            WHERE user_policy_year_id IN (
                                SELECT id
                                FROM user_policy_years
                                WHERE user_id = ?
                            )
                        )
                        """,
                user.getId()
        );
        jdbcTemplate.update(
                """
                        DELETE FROM receipt_processing_attempts
                        WHERE receipt_id IN (
                            SELECT id
                            FROM receipts
                            WHERE user_policy_year_id IN (
                                SELECT id
                                FROM user_policy_years
                                WHERE user_id = ?
                            )
                        )
                        """,
                user.getId()
        );
        jdbcTemplate.update(
                """
                        DELETE FROM receipt_upload_intents
                        WHERE user_policy_year_id IN (
                            SELECT id
                            FROM user_policy_years
                            WHERE user_id = ?
                        )
                        """,
                user.getId()
        );
        jdbcTemplate.update(
                """
                        DELETE FROM receipts
                        WHERE user_policy_year_id IN (
                            SELECT id
                            FROM user_policy_years
                            WHERE user_id = ?
                        )
                        """,
                user.getId()
        );
        jdbcTemplate.update(
                """
                        DELETE FROM user_relief_claims
                        WHERE user_policy_year_id IN (
                            SELECT id
                            FROM user_policy_years
                            WHERE user_id = ?
                        )
                        """,
                user.getId()
        );
        jdbcTemplate.update("DELETE FROM user_policy_years WHERE user_id = ?", user.getId());

        user.resetProfile();

        storedReceiptIds.forEach(this::tryDeleteStoredFile);
    }

    private AppUser getCurrentUserEntity() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        return appUserRepository.findById(currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Current user " + currentUser.email() + " was not found"
                ));
    }

    private ProfileResponse toResponse(AppUser user) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.isDisabled(),
                user.getMaritalStatus(),
                user.getSpouseDisabled(),
                user.getSpouseWorking(),
                user.getHasChildren()
        );
    }

    private void tryDeleteStoredFile(String receiptKey) {
        try {
            receiptStorageService.delete(receiptKey);
        } catch (IOException ignored) {
        }
    }

    private void deleteUserOwnedRowsIfPresent(String tableName, UUID userId) {
        if (!tableExists(tableName)) {
            return;
        }

        try {
            jdbcTemplate.update("DELETE FROM " + tableName + " WHERE user_id = ?", userId);
        } catch (DataAccessException ignored) {
        }
    }

    private boolean tableExists(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String normalizedTableName = tableName.toUpperCase(Locale.ROOT);

            try (ResultSet tables = metadata.getTables(null, null, normalizedTableName, null)) {
                if (tables.next()) {
                    return true;
                }
            }

            try (ResultSet tables = metadata.getTables(null, null, tableName.toLowerCase(Locale.ROOT), null)) {
                return tables.next();
            }
        } catch (SQLException exception) {
            return false;
        }
    }
}
