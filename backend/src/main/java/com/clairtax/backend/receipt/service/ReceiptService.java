package com.clairtax.backend.receipt.service;

import com.clairtax.backend.calculator.entity.ReliefCategory;
import com.clairtax.backend.calculator.exception.CalculatorValidationException;
import com.clairtax.backend.calculator.exception.ResourceNotFoundException;
import com.clairtax.backend.calculator.repository.ReliefCategoryRepository;
import com.clairtax.backend.policyyear.entity.PolicyYear;
import com.clairtax.backend.policyyear.repository.PolicyYearRepository;
import com.clairtax.backend.receipt.config.ReceiptProcessingProperties;
import com.clairtax.backend.receipt.dto.ConfirmReceiptReviewRequest;
import com.clairtax.backend.receipt.dto.ConfirmReceiptUploadRequest;
import com.clairtax.backend.receipt.dto.CreateReceiptRequest;
import com.clairtax.backend.receipt.dto.CreateReceiptUploadIntentRequest;
import com.clairtax.backend.receipt.dto.DirectReceiptUploadRequest;
import com.clairtax.backend.receipt.dto.ReceiptLatestExtractionResponse;
import com.clairtax.backend.receipt.dto.ReceiptResponse;
import com.clairtax.backend.receipt.dto.ReceiptUploadIntentResponse;
import com.clairtax.backend.receipt.dto.RejectReceiptReviewRequest;
import com.clairtax.backend.receipt.dto.UpdateReceiptRequest;
import com.clairtax.backend.receipt.dto.internal.ReceiptExtractionResultRequest;
import com.clairtax.backend.receipt.dto.internal.ReceiptProcessingAttemptRequest;
import com.clairtax.backend.receipt.dto.internal.ReviewedReceiptTrainingExampleResponse;
import com.clairtax.backend.receipt.entity.Receipt;
import com.clairtax.backend.receipt.entity.ReceiptExtractionResult;
import com.clairtax.backend.receipt.entity.ReceiptProcessingAttempt;
import com.clairtax.backend.receipt.entity.ReceiptReviewAction;
import com.clairtax.backend.receipt.entity.ReceiptUploadIntent;
import com.clairtax.backend.receipt.model.ReceiptProcessingAttemptStatus;
import com.clairtax.backend.receipt.model.ReceiptReviewActionType;
import com.clairtax.backend.receipt.model.ReceiptStatus;
import com.clairtax.backend.receipt.queue.ReceiptJobPublisher;
import com.clairtax.backend.receipt.queue.ReceiptProcessingJobMessage;
import com.clairtax.backend.receipt.repository.ReceiptExtractionResultRepository;
import com.clairtax.backend.receipt.repository.ReceiptProcessingAttemptRepository;
import com.clairtax.backend.receipt.repository.ReceiptRepository;
import com.clairtax.backend.receipt.repository.ReceiptReviewActionRepository;
import com.clairtax.backend.receipt.repository.ReceiptUploadIntentRepository;
import com.clairtax.backend.receipt.storage.ReceiptObjectStorageService;
import com.clairtax.backend.receipt.storage.ReceiptUploadTarget;
import com.clairtax.backend.reliefclaim.service.UserReliefClaimSyncService;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import com.clairtax.backend.user.service.CurrentUser;
import com.clairtax.backend.user.service.CurrentUserProvider;
import com.clairtax.backend.user.service.ProfileReliefResolver;
import com.clairtax.backend.useryear.entity.UserPolicyYear;
import com.clairtax.backend.useryear.repository.UserPolicyYearRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class ReceiptService {

    private static final String JOB_SCHEMA_VERSION = "2026-03-27";

    private final ReceiptRepository receiptRepository;
    private final ReceiptUploadIntentRepository receiptUploadIntentRepository;
    private final ReceiptProcessingAttemptRepository receiptProcessingAttemptRepository;
    private final ReceiptExtractionResultRepository receiptExtractionResultRepository;
    private final ReceiptReviewActionRepository receiptReviewActionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final PolicyYearRepository policyYearRepository;
    private final UserPolicyYearRepository userPolicyYearRepository;
    private final ReliefCategoryRepository reliefCategoryRepository;
    private final UserReliefClaimSyncService userReliefClaimSyncService;
    private final ReceiptObjectStorageService receiptObjectStorageService;
    private final ReceiptJobPublisher receiptJobPublisher;
    private final ProfileReliefResolver profileReliefResolver;
    private final ReceiptProcessingProperties properties;

    public ReceiptService(
            ReceiptRepository receiptRepository,
            ReceiptUploadIntentRepository receiptUploadIntentRepository,
            ReceiptProcessingAttemptRepository receiptProcessingAttemptRepository,
            ReceiptExtractionResultRepository receiptExtractionResultRepository,
            ReceiptReviewActionRepository receiptReviewActionRepository,
            CurrentUserProvider currentUserProvider,
            AppUserRepository appUserRepository,
            PolicyYearRepository policyYearRepository,
            UserPolicyYearRepository userPolicyYearRepository,
            ReliefCategoryRepository reliefCategoryRepository,
            UserReliefClaimSyncService userReliefClaimSyncService,
            ReceiptObjectStorageService receiptObjectStorageService,
            ReceiptJobPublisher receiptJobPublisher,
            ProfileReliefResolver profileReliefResolver,
            ReceiptProcessingProperties properties
    ) {
        this.receiptRepository = receiptRepository;
        this.receiptUploadIntentRepository = receiptUploadIntentRepository;
        this.receiptProcessingAttemptRepository = receiptProcessingAttemptRepository;
        this.receiptExtractionResultRepository = receiptExtractionResultRepository;
        this.receiptReviewActionRepository = receiptReviewActionRepository;
        this.currentUserProvider = currentUserProvider;
        this.appUserRepository = appUserRepository;
        this.policyYearRepository = policyYearRepository;
        this.userPolicyYearRepository = userPolicyYearRepository;
        this.reliefCategoryRepository = reliefCategoryRepository;
        this.userReliefClaimSyncService = userReliefClaimSyncService;
        this.receiptObjectStorageService = receiptObjectStorageService;
        this.receiptJobPublisher = receiptJobPublisher;
        this.profileReliefResolver = profileReliefResolver;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<Integer> getReceiptYears() {
        return receiptRepository.findDistinctPolicyYearsByUserId(getCurrentUser().id());
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> getReceipts(Integer year) {
        CurrentUser currentUser = getCurrentUser();
        List<Receipt> receipts = year == null
                ? receiptRepository.findAllDetailedByUserId(currentUser.id())
                : receiptRepository.findAllDetailedByUserIdAndPolicyYear(currentUser.id(), year);

        return receipts.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(UUID id) {
        return toResponse(findReceiptForCurrentUser(id));
    }

    public ReceiptResponse createReceipt(CreateReceiptRequest request) {
        AppUser currentUser = getCurrentUserEntity();
        UserPolicyYear userPolicyYear = getOrCreateUserPolicyYear(request.policyYear(), currentUser);
        ReliefCategory reliefCategory = resolveReliefCategory(
                request.reliefCategoryId(),
                userPolicyYear.getPolicyYear(),
                currentUser
        );

        Receipt receipt = new Receipt(
                userPolicyYear,
                reliefCategory,
                normalizeOptionalString(request.notes()),
                normalizeRequiredString(request.fileName(), "receipt.pdf"),
                normalizeRequiredString(request.fileUrl(), "https://example.com/manual"),
                properties.getBucketName(),
                "manual/" + UUID.randomUUID(),
                "application/octet-stream",
                0,
                UUID.randomUUID().toString().replace("-", ""),
                ReceiptStatus.VERIFIED,
                OffsetDateTime.now()
        );
        receipt.confirmReview(
                userPolicyYear,
                reliefCategory,
                request.merchantName().trim(),
                request.receiptDate(),
                request.amount(),
                "MYR",
                normalizeOptionalString(request.notes())
        );

        Receipt createdReceipt = receiptRepository.save(receipt);
        syncClaim(userPolicyYear.getId(), reliefCategory);
        return toResponse(createdReceipt);
    }

    public ReceiptResponse uploadReceipt(Integer year, DirectReceiptUploadRequest fields, MultipartFile file) {
        String mimeType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!mimeType.equals("application/pdf") && !mimeType.equals("image/png") && !mimeType.equals("image/jpeg")) {
            throw new CalculatorValidationException("Only PDF, PNG, and JPG files are accepted.");
        }
        if (file.getSize() > 10L * 1024L * 1024L) {
            throw new CalculatorValidationException("File is too large. Please upload a file smaller than 10MB.");
        }

        AppUser currentUser = getCurrentUserEntity();
        UserPolicyYear userPolicyYear = findExistingUserPolicyYear(year);
        ReliefCategory reliefCategory = resolveReliefCategory(
                fields.reliefCategoryId(),
                userPolicyYear.getPolicyYear(),
                currentUser
        );

        String sanitizedFileName = sanitizeFileName(
                normalizeRequiredString(file.getOriginalFilename(), "receipt")
        );
        String objectKey = "receipts-%d-%s-%s-%s".formatted(
                year,
                currentUser.getId(),
                UUID.randomUUID(),
                sanitizedFileName
        );

        try {
            receiptObjectStorageService.storeUploadedObject(objectKey, file.getInputStream());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to upload receipt file to storage", exception);
        }

        Receipt receipt = new Receipt(
                userPolicyYear,
                reliefCategory,
                normalizeOptionalString(fields.notes()),
                sanitizedFileName,
                "pending",
                properties.getBucketName(),
                objectKey,
                mimeType,
                file.getSize(),
                UUID.randomUUID().toString().replace("-", ""),
                ReceiptStatus.VERIFIED,
                OffsetDateTime.now()
        );
        receipt.confirmReview(
                userPolicyYear,
                reliefCategory,
                fields.merchantName().trim(),
                fields.receiptDate(),
                fields.amount(),
                "MYR",
                normalizeOptionalString(fields.notes())
        );

        Receipt saved = receiptRepository.save(receipt);
        saved.assignFileUrl(buildStoredFileUrl(saved.getId()));
        syncClaim(userPolicyYear.getId(), reliefCategory);
        return toResponse(saved);
    }

    public ReceiptResponse updateReceipt(UUID id, UpdateReceiptRequest request) {
        Receipt receipt = findReceiptForCurrentUser(id);
        UUID previousUserPolicyYearId = receipt.getUserPolicyYear().getId();
        UUID previousReliefCategoryId = receipt.getReliefCategory() == null ? null : receipt.getReliefCategory().getId();
        AppUser currentUser = getCurrentUserEntity();
        UserPolicyYear userPolicyYear = getOrCreateUserPolicyYear(request.policyYear(), currentUser);
        ReliefCategory reliefCategory = resolveReliefCategory(
                request.reliefCategoryId(),
                userPolicyYear.getPolicyYear(),
                currentUser
        );

        receipt.updateManualDetails(
                userPolicyYear,
                reliefCategory,
                request.merchantName().trim(),
                request.receiptDate(),
                request.amount(),
                normalizeOptionalString(request.notes()),
                normalizeRequiredString(request.fileName(), receipt.getFileName()),
                normalizeRequiredString(request.fileUrl(), receipt.getFileUrl())
        );
        if (request.fileUrl() != null) {
            // Manual edits can switch to externally hosted files in dev tooling.
        }

        syncClaim(previousUserPolicyYearId, previousReliefCategoryId);
        syncClaim(userPolicyYear.getId(), reliefCategory);
        return toResponse(receipt);
    }

    public void deleteReceipt(UUID id) {
        Receipt receipt = findReceiptForCurrentUser(id);
        UUID userPolicyYearId = receipt.getUserPolicyYear().getId();
        UUID reliefCategoryId = receipt.getReliefCategory() == null ? null : receipt.getReliefCategory().getId();

        receiptReviewActionRepository.deleteByReceiptId(receipt.getId());
        receiptExtractionResultRepository.deleteByReceiptId(receipt.getId());
        receiptProcessingAttemptRepository.deleteByReceiptId(receipt.getId());
        receiptRepository.delete(receipt);
        syncClaim(userPolicyYearId, reliefCategoryId);

        tryDeleteStoredFile(receipt.getS3Key());
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> getReceiptsForUserYear(Integer year) {
        UserPolicyYear userPolicyYear = findExistingUserPolicyYear(year);
        return receiptRepository.findAllDetailedByUserPolicyYearId(userPolicyYear.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ReceiptUploadIntentResponse createUploadIntent(Integer year, CreateReceiptUploadIntentRequest request) {
        UserPolicyYear userPolicyYear = findExistingUserPolicyYear(year);
        AppUser currentUser = getCurrentUserEntity();
        ReliefCategory reliefCategory = resolveReliefCategory(
                request.reliefCategoryId(),
                userPolicyYear.getPolicyYear(),
                currentUser
        );
        validateUploadRequest(request);

        String objectKey = "receipts-%d-%s-%s-%s".formatted(
                year,
                currentUser.getId(),
                UUID.randomUUID(),
                sanitizeFileName(request.fileName())
        );
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(Duration.parse(properties.getUploadIntentTtl()));
        ReceiptUploadIntent intent = receiptUploadIntentRepository.save(new ReceiptUploadIntent(
                userPolicyYear,
                reliefCategory,
                objectKey,
                sanitizeFileName(request.fileName()),
                request.mimeType().trim(),
                request.fileSizeBytes(),
                expiresAt
        ));

        try {
            ReceiptUploadTarget uploadTarget = receiptObjectStorageService.createUploadTarget(
                    objectKey,
                    request.mimeType().trim()
            );
            return new ReceiptUploadIntentResponse(
                    intent.getId(),
                    uploadTarget.uploadUrl(),
                    uploadTarget.method(),
                    uploadTarget.headers(),
                    uploadTarget.expiresAt()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to prepare a receipt upload target", exception);
        }
    }

    public ReceiptResponse confirmUpload(Integer year, ConfirmReceiptUploadRequest request) {
        CurrentUser currentUser = getCurrentUser();
        ReceiptUploadIntent intent = receiptUploadIntentRepository.findByIdAndUserPolicyYearUserId(
                        request.uploadIntentId(),
                        currentUser.id()
                )
                .orElseThrow(() -> new ResourceNotFoundException("Receipt upload intent " + request.uploadIntentId() + " not found"));

        if (!Objects.equals(intent.getUserPolicyYear().getPolicyYear().getYear(), year)) {
            throw new CalculatorValidationException("Upload intent does not belong to year " + year);
        }
        if (intent.getCompletedAt() != null) {
            throw new CalculatorValidationException("Receipt upload intent was already completed");
        }
        if (intent.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new CalculatorValidationException("Receipt upload intent has expired");
        }

        validateUploadedObject(intent);

        String sha256Hash = computeSha256(intent.getObjectKey());

        Receipt receipt = receiptRepository.save(new Receipt(
                intent.getUserPolicyYear(),
                intent.getReliefCategory(),
                normalizeOptionalString(request.notes()),
                intent.getOriginalFilename(),
                "/api/receipts/pending-file",
                properties.getBucketName(),
                intent.getObjectKey(),
                intent.getMimeType(),
                intent.getFileSizeBytes(),
                sha256Hash,
                ReceiptStatus.UPLOADED,
                OffsetDateTime.now()
        ));
        receipt.assignFileUrl(buildStoredFileUrl(receipt.getId()));
        UUID jobId = UUID.randomUUID();
        receipt.markProcessing();
        receiptProcessingAttemptRepository.save(new ReceiptProcessingAttempt(
                receipt,
                jobId,
                ReceiptProcessingAttemptStatus.QUEUED
        ));
        intent.markUploaded();
        intent.markCompleted();

        receiptJobPublisher.publish(new ReceiptProcessingJobMessage(
                JOB_SCHEMA_VERSION,
                jobId,
                receipt.getId(),
                currentUser.id(),
                year,
                receipt.getReliefCategory() == null ? null : receipt.getReliefCategory().getId(),
                receipt.getS3Bucket(),
                receipt.getS3Key(),
                receipt.getMimeType(),
                receipt.getFileSizeBytes(),
                receipt.getSha256Hash(),
                receipt.getUploadedAt(),
                currentUser.id().toString()
        ));

        return toResponse(receipt);
    }

    public ReceiptResponse confirmReview(UUID receiptId, ConfirmReceiptReviewRequest request) {
        Receipt receipt = findReceiptForCurrentUser(receiptId);
        ReceiptExtractionResult latestExtraction = findLatestExtraction(receipt.getId());
        UUID previousUserPolicyYearId = receipt.getUserPolicyYear().getId();
        UUID previousReliefCategoryId = receipt.getReliefCategory() == null ? null : receipt.getReliefCategory().getId();
        AppUser currentUser = getCurrentUserEntity();
        int targetYear = receipt.getPolicyYear();
        UserPolicyYear userPolicyYear = getOrCreateUserPolicyYear(targetYear, currentUser);
        ReliefCategory reliefCategory = resolveReliefCategory(
                request.reliefCategoryId() == null
                        ? (receipt.getReliefCategory() == null ? null : receipt.getReliefCategory().getId())
                        : request.reliefCategoryId(),
                userPolicyYear.getPolicyYear(),
                currentUser
        );

        String merchantName = normalizeOptionalString(request.merchantName());
        if (merchantName == null && latestExtraction != null) {
            merchantName = latestExtraction.getMerchantName();
        }
        if (merchantName == null) {
            merchantName = "Receipt";
        }

        receipt.confirmReview(
                userPolicyYear,
                reliefCategory,
                merchantName,
                request.receiptDate() != null
                        ? request.receiptDate()
                        : latestExtraction == null ? null : latestExtraction.getReceiptDate(),
                request.amount() != null
                        ? request.amount()
                        : latestExtraction == null ? BigDecimal.ZERO : latestExtraction.getTotalAmount(),
                normalizeRequiredString(
                        normalizeOptionalString(request.currency()),
                        latestExtraction == null ? "MYR" : normalizeRequiredString(latestExtraction.getCurrencyCode(), "MYR")
                ),
                normalizeOptionalString(request.notes()) == null ? receipt.getNotes() : normalizeOptionalString(request.notes())
        );
        if (receipt.getReceiptDate() == null) {
            throw new CalculatorValidationException("Receipt date is required before confirming the receipt");
        }
        if (receipt.getAmount() == null) {
            throw new CalculatorValidationException("Receipt amount is required before confirming the receipt");
        }

        receiptReviewActionRepository.save(new ReceiptReviewAction(
                receipt,
                ReceiptReviewActionType.CONFIRMED,
                receipt.getMerchantName(),
                receipt.getReceiptDate(),
                receipt.getAmount(),
                receipt.getCurrencyCode(),
                normalizeOptionalString(request.notes()),
                null,
                null
        ));

        syncClaim(previousUserPolicyYearId, previousReliefCategoryId);
        syncClaim(userPolicyYear.getId(), reliefCategory);
        return toResponse(receipt);
    }

    public ReceiptResponse rejectReview(UUID receiptId, RejectReceiptReviewRequest request) {
        Receipt receipt = findReceiptForCurrentUser(receiptId);
        UUID userPolicyYearId = receipt.getUserPolicyYear().getId();
        UUID reliefCategoryId = receipt.getReliefCategory() == null ? null : receipt.getReliefCategory().getId();
        String invalidReasonCode = normalizeOptionalString(request.invalidReasonCode()) == null
                ? normalizeOptionalString(receipt.getProcessingErrorCode())
                : normalizeOptionalString(request.invalidReasonCode());
        String invalidReasonMessage = normalizeOptionalString(request.invalidReasonMessage()) == null
                ? normalizeOptionalString(receipt.getProcessingErrorMessage())
                : normalizeOptionalString(request.invalidReasonMessage());

        receipt.rejectReview(
                normalizeOptionalString(request.notes()) == null ? receipt.getNotes() : normalizeOptionalString(request.notes()),
                invalidReasonCode,
                invalidReasonMessage
        );
        receiptReviewActionRepository.save(new ReceiptReviewAction(
                receipt,
                ReceiptReviewActionType.REJECTED,
                receipt.getMerchantName(),
                receipt.getReceiptDate(),
                receipt.getAmount(),
                receipt.getCurrencyCode(),
                normalizeOptionalString(request.notes()),
                invalidReasonCode,
                invalidReasonMessage
        ));
        syncClaim(userPolicyYearId, reliefCategoryId);
        return toResponse(receipt);
    }

    public void recordProcessingAttempt(UUID receiptId, ReceiptProcessingAttemptRequest request) {
        Receipt receipt = findReceiptById(receiptId);
        ReceiptProcessingAttemptStatus status = parseAttemptStatus(request.status());
        ReceiptProcessingAttempt attempt = receiptProcessingAttemptRepository.findByJobId(request.jobId())
                .orElseGet(() -> receiptProcessingAttemptRepository.save(
                        new ReceiptProcessingAttempt(receipt, request.jobId(), status)
                ));
        attempt.updateStatus(status, normalizeOptionalString(request.errorCode()), normalizeOptionalString(request.errorMessage()));

        if (status == ReceiptProcessingAttemptStatus.PROCESSING) {
            receipt.markProcessing();
        } else if (status == ReceiptProcessingAttemptStatus.FAILED) {
            receipt.markFailed(
                    normalizeOptionalString(request.errorCode()),
                    normalizeOptionalString(request.errorMessage())
            );
        }
    }

    public void recordExtractionResult(UUID receiptId, ReceiptExtractionResultRequest request) {
        Receipt receipt = findReceiptById(receiptId);
        if (receiptExtractionResultRepository.existsByJobId(request.jobId())) {
            throw new CalculatorValidationException("Extraction result for job " + request.jobId() + " was already recorded");
        }

        receiptExtractionResultRepository.save(new ReceiptExtractionResult(
                receipt,
                request.jobId(),
                request.totalAmount(),
                request.receiptDate(),
                normalizeOptionalString(request.merchantName()),
                normalizeOptionalString(request.currency()),
                request.confidenceScore(),
                joinWarnings(request.warnings()),
                request.rawPayloadJson(),
                request.providerName(),
                request.providerVersion(),
                request.processedAt(),
                normalizeOptionalString(request.errorCode()),
                normalizeOptionalString(request.errorMessage())
        ));
        if (normalizeOptionalString(request.errorCode()) == null) {
            receipt.markProcessed();
            receiptProcessingAttemptRepository.findByJobId(request.jobId())
                    .ifPresent(attempt -> attempt.updateStatus(ReceiptProcessingAttemptStatus.COMPLETED, null, null));
        } else {
            receipt.markFailed(
                    normalizeOptionalString(request.errorCode()),
                    normalizeOptionalString(request.errorMessage())
            );
            receiptProcessingAttemptRepository.findByJobId(request.jobId())
                    .ifPresent(attempt -> attempt.updateStatus(
                            ReceiptProcessingAttemptStatus.FAILED,
                            normalizeOptionalString(request.errorCode()),
                            normalizeOptionalString(request.errorMessage())
                    ));
        }
    }

    @Transactional(readOnly = true)
    public List<ReviewedReceiptTrainingExampleResponse> exportReviewedReceiptTrainingExamples() {
        return receiptRepository.findAllByStatusInOrderByUpdatedAtDesc(
                        List.of(ReceiptStatus.VERIFIED, ReceiptStatus.REJECTED)
                )
                .stream()
                .map(this::toReviewedTrainingExampleResponse)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public Resource loadReceiptFile(UUID id) {
        Receipt receipt = findReceiptForCurrentUser(id);
        try {
            return receiptObjectStorageService.load(receipt.getS3Key());
        } catch (IOException exception) {
            throw new ResourceNotFoundException("Receipt file " + id + " not found");
        }
    }

    private Receipt findReceiptForCurrentUser(UUID id) {
        return receiptRepository.findDetailedByIdAndUserId(id, getCurrentUser().id())
                .orElseThrow(() -> new ResourceNotFoundException("Receipt " + id + " not found"));
    }

    private Receipt findReceiptById(UUID id) {
        return receiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt " + id + " not found"));
    }

    private PolicyYear findPolicyYear(Integer year) {
        return policyYearRepository.findByYear(year)
                .orElseThrow(() -> new CalculatorValidationException("Policy year " + year + " not found"));
    }

    private ReliefCategory resolveReliefCategory(UUID reliefCategoryId, PolicyYear policyYear, AppUser currentUser) {
        if (reliefCategoryId == null) {
            return null;
        }

        ReliefCategory reliefCategory = reliefCategoryRepository.findById(reliefCategoryId)
                .orElseThrow(() -> new CalculatorValidationException("Relief category " + reliefCategoryId + " not found"));
        if (!policyYear.getId().equals(reliefCategory.getPolicyYearId())) {
            throw new CalculatorValidationException(
                    "Relief category " + reliefCategoryId + " does not belong to policy year " + policyYear.getYear()
            );
        }
        if (!profileReliefResolver.isCategoryVisible(currentUser, reliefCategory)) {
            throw new CalculatorValidationException(
                    "Relief category " + reliefCategory.getName() + " is not available for the saved profile"
            );
        }

        return reliefCategory;
    }

    private UserPolicyYear getOrCreateUserPolicyYear(Integer year, AppUser currentUser) {
        return userPolicyYearRepository.findByUserIdAndPolicyYearYear(currentUser.getId(), year)
                .orElseGet(() -> userPolicyYearRepository.save(new UserPolicyYear(currentUser, findPolicyYear(year))));
    }

    private UserPolicyYear findExistingUserYearForCurrentUser(Integer year) {
        return userPolicyYearRepository.findByUserIdAndPolicyYearYear(getCurrentUser().id(), year)
                .orElseThrow(() -> new ResourceNotFoundException("Year workspace " + year + " not found for the current user"));
    }

    private UserPolicyYear findExistingUserPolicyYear(Integer year) {
        return findExistingUserYearForCurrentUser(year);
    }

    private void syncClaim(UUID userPolicyYearId, ReliefCategory reliefCategory) {
        syncClaim(userPolicyYearId, reliefCategory == null ? null : reliefCategory.getId());
    }

    private void syncClaim(UUID userPolicyYearId, UUID reliefCategoryId) {
        userReliefClaimSyncService.syncClaim(userPolicyYearId, reliefCategoryId);
    }

    private ReceiptResponse toResponse(Receipt receipt) {
        ReceiptExtractionResult latestExtraction = findLatestExtraction(receipt.getId());
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getPolicyYear(),
                receipt.getMerchantName(),
                receipt.getReceiptDate(),
                receipt.getAmount(),
                receipt.getCurrencyCode(),
                receipt.getReliefCategory() == null ? null : receipt.getReliefCategory().getId(),
                receipt.getReliefCategory() == null ? null : receipt.getReliefCategory().getName(),
                receipt.getNotes(),
                receipt.getFileName(),
                receipt.getFileUrl(),
                receipt.getMimeType(),
                receipt.getFileSizeBytes(),
                receipt.getStatus().name().toLowerCase(),
                receipt.getProcessingErrorCode(),
                receipt.getProcessingErrorMessage(),
                latestExtraction == null ? null : toLatestExtractionResponse(latestExtraction),
                receipt.getUploadedAt(),
                receipt.getCreatedAt(),
                receipt.getUpdatedAt()
        );
    }

    private ReceiptLatestExtractionResponse toLatestExtractionResponse(ReceiptExtractionResult result) {
        return new ReceiptLatestExtractionResponse(
                result.getTotalAmount(),
                result.getReceiptDate(),
                result.getMerchantName(),
                result.getCurrencyCode(),
                result.getConfidenceScore(),
                splitWarnings(result.getWarningMessages()),
                result.getProviderName(),
                result.getProviderVersion(),
                result.getProcessedAt(),
                result.getErrorCode(),
                result.getErrorMessage()
        );
    }

    private ReceiptExtractionResult findLatestExtraction(UUID receiptId) {
        return receiptExtractionResultRepository.findTopByReceiptIdOrderByProcessedAtDesc(receiptId).orElse(null);
    }

    private ReceiptReviewAction findLatestReviewAction(UUID receiptId) {
        return receiptReviewActionRepository.findTopByReceiptIdOrderByCreatedAtDesc(receiptId).orElse(null);
    }

    private ReviewedReceiptTrainingExampleResponse toReviewedTrainingExampleResponse(Receipt receipt) {
        ReceiptExtractionResult latestExtraction = findLatestExtraction(receipt.getId());
        ReceiptReviewAction latestReviewAction = findLatestReviewAction(receipt.getId());
        if (latestExtraction == null || latestReviewAction == null) {
            return null;
        }

        boolean isValidReceipt = receipt.getStatus() == ReceiptStatus.VERIFIED;
        String invalidReason = isValidReceipt
                ? null
                : normalizeRequiredString(
                        latestReviewAction.getInvalidReasonCode(),
                        normalizeRequiredString(receipt.getProcessingErrorCode(), "rejected_by_user")
                );
        String invalidReasonMessage = isValidReceipt
                ? null
                : normalizeRequiredString(
                        latestReviewAction.getInvalidReasonMessage(),
                        receipt.getProcessingErrorMessage()
                );

        return new ReviewedReceiptTrainingExampleResponse(
                receipt.getId(),
                receipt.getPolicyYear(),
                receipt.getFileName(),
                receipt.getFileUrl(),
                receipt.getS3Bucket(),
                receipt.getS3Key(),
                receipt.getMimeType(),
                receipt.getFileSizeBytes(),
                isValidReceipt,
                invalidReason,
                invalidReasonMessage,
                isValidReceipt ? receipt.getAmount() : null,
                isValidReceipt ? receipt.getReceiptDate() : null,
                isValidReceipt ? receipt.getMerchantName() : latestReviewAction.getMerchantName(),
                isValidReceipt ? receipt.getCurrencyCode() : latestReviewAction.getCurrencyCode(),
                latestExtraction.getRawPayloadJson(),
                latestExtraction.getProviderName(),
                latestExtraction.getProviderVersion(),
                isValidReceipt ? "user_review_confirmed" : "user_review_rejected",
                latestReviewAction.getCreatedAt()
        );
    }

    private CurrentUser getCurrentUser() {
        return currentUserProvider.getCurrentUser();
    }

    private AppUser getCurrentUserEntity() {
        CurrentUser currentUser = getCurrentUser();
        return appUserRepository.findById(currentUser.id())
                .orElseThrow(() -> new IllegalStateException("Current user " + currentUser.email() + " was not found"));
    }

    private void validateUploadRequest(CreateReceiptUploadIntentRequest request) {
        String mimeType = request.mimeType().toLowerCase();
        if (!(mimeType.equals("application/pdf") || mimeType.startsWith("image/"))) {
            throw new CalculatorValidationException("Unsupported receipt file type. Please upload a PDF or image.");
        }
        if (request.fileSizeBytes() > 10L * 1024L * 1024L) {
            throw new CalculatorValidationException("Receipt file is too large. Please upload a file smaller than 10MB.");
        }
    }

    private void validateUploadedObject(ReceiptUploadIntent intent) {
        try {
            if (!receiptObjectStorageService.exists(intent.getObjectKey())) {
                throw new CalculatorValidationException("The receipt file was not uploaded yet");
            }
            long storedSize = receiptObjectStorageService.size(intent.getObjectKey());
            if (storedSize != intent.getFileSizeBytes()) {
                throw new CalculatorValidationException("Uploaded receipt size did not match the original upload intent");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to validate the uploaded receipt file", exception);
        }
    }

    private String computeSha256(String objectKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Resource resource = receiptObjectStorageService.load(objectKey);
            InputStream inputStream = resource.getInputStream();
            try (InputStream stream = inputStream) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to calculate the uploaded receipt checksum", exception);
        }
    }

    private void tryDeleteStoredFile(String objectKey) {
        try {
            receiptObjectStorageService.delete(objectKey);
        } catch (IOException ignored) {
        }
    }

    private String buildStoredFileUrl(UUID receiptId) {
        return "/api/receipts/" + receiptId + "/file";
    }

    private ReceiptProcessingAttemptStatus parseAttemptStatus(String status) {
        try {
            return ReceiptProcessingAttemptStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new CalculatorValidationException("Unknown receipt processing status " + status);
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String normalizeOptionalString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequiredString(String value, String fallback) {
        String normalized = normalizeOptionalString(value);
        return normalized == null ? fallback : normalized;
    }

    private String joinWarnings(List<String> warnings) {
        return String.join("\n", warnings);
    }

    private List<String> splitWarnings(String warningMessages) {
        if (warningMessages == null || warningMessages.isBlank()) {
            return List.of();
        }
        return Arrays.stream(warningMessages.split("\\n"))
                .filter(message -> !message.isBlank())
                .map(String::trim)
                .toList();
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
