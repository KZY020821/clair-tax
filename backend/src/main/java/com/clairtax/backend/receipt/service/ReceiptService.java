package com.clairtax.backend.receipt.service;

import com.clairtax.backend.calculator.entity.ReliefCategory;
import com.clairtax.backend.calculator.exception.CalculatorValidationException;
import com.clairtax.backend.calculator.exception.ResourceNotFoundException;
import com.clairtax.backend.calculator.repository.ReliefCategoryRepository;
import com.clairtax.backend.policyyear.entity.PolicyYear;
import com.clairtax.backend.policyyear.repository.PolicyYearRepository;
import com.clairtax.backend.receipt.dto.CreateReceiptRequest;
import com.clairtax.backend.receipt.dto.ReceiptResponse;
import com.clairtax.backend.receipt.dto.UpdateReceiptRequest;
import com.clairtax.backend.receipt.dto.UploadYearReceiptRequest;
import com.clairtax.backend.receipt.entity.Receipt;
import com.clairtax.backend.receipt.repository.ReceiptRepository;
import com.clairtax.backend.receipt.storage.ReceiptStorageService;
import com.clairtax.backend.reliefclaim.service.UserReliefClaimSyncService;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import com.clairtax.backend.user.service.CurrentUser;
import com.clairtax.backend.user.service.CurrentUserProvider;
import com.clairtax.backend.user.service.ProfileReliefResolver;
import com.clairtax.backend.useryear.entity.UserPolicyYear;
import com.clairtax.backend.useryear.repository.UserPolicyYearRepository;
import org.springframework.core.io.Resource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Profile("!local")
@Transactional
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final PolicyYearRepository policyYearRepository;
    private final UserPolicyYearRepository userPolicyYearRepository;
    private final ReliefCategoryRepository reliefCategoryRepository;
    private final UserReliefClaimSyncService userReliefClaimSyncService;
    private final ReceiptStorageService receiptStorageService;
    private final ProfileReliefResolver profileReliefResolver;

    public ReceiptService(
            ReceiptRepository receiptRepository,
            CurrentUserProvider currentUserProvider,
            AppUserRepository appUserRepository,
            PolicyYearRepository policyYearRepository,
            UserPolicyYearRepository userPolicyYearRepository,
            ReliefCategoryRepository reliefCategoryRepository,
            UserReliefClaimSyncService userReliefClaimSyncService,
            ReceiptStorageService receiptStorageService,
            ProfileReliefResolver profileReliefResolver
    ) {
        this.receiptRepository = receiptRepository;
        this.currentUserProvider = currentUserProvider;
        this.appUserRepository = appUserRepository;
        this.policyYearRepository = policyYearRepository;
        this.userPolicyYearRepository = userPolicyYearRepository;
        this.reliefCategoryRepository = reliefCategoryRepository;
        this.userReliefClaimSyncService = userReliefClaimSyncService;
        this.receiptStorageService = receiptStorageService;
        this.profileReliefResolver = profileReliefResolver;
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

        return receipts.stream()
                .map(this::toResponse)
                .toList();
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
                request.merchantName().trim(),
                request.receiptDate(),
                request.amount(),
                reliefCategory,
                normalizeOptionalString(request.notes()),
                normalizeOptionalString(request.fileName()),
                normalizeOptionalString(request.fileUrl())
        );

        Receipt createdReceipt = receiptRepository.save(receipt);
        syncClaim(userPolicyYear.getId(), reliefCategory);

        return toResponse(createdReceipt);
    }

    public ReceiptResponse updateReceipt(UUID id, UpdateReceiptRequest request) {
        Receipt receipt = findReceiptForCurrentUser(id);
        UUID previousUserPolicyYearId = receipt.getUserPolicyYear().getId();
        UUID previousReliefCategoryId = receipt.getReliefCategory() == null
                ? null
                : receipt.getReliefCategory().getId();
        AppUser currentUser = getCurrentUserEntity();
        UserPolicyYear userPolicyYear = getOrCreateUserPolicyYear(request.policyYear(), currentUser);
        ReliefCategory reliefCategory = resolveReliefCategory(
                request.reliefCategoryId(),
                userPolicyYear.getPolicyYear(),
                currentUser
        );

        receipt.update(
                userPolicyYear,
                request.merchantName().trim(),
                request.receiptDate(),
                request.amount(),
                reliefCategory,
                normalizeOptionalString(request.notes()),
                normalizeOptionalString(request.fileName()),
                normalizeOptionalString(request.fileUrl())
        );

        syncClaim(previousUserPolicyYearId, previousReliefCategoryId);
        syncClaim(userPolicyYear.getId(), reliefCategory);

        return toResponse(receipt);
    }

    public void deleteReceipt(UUID id) {
        Receipt receipt = findReceiptForCurrentUser(id);
        UUID userPolicyYearId = receipt.getUserPolicyYear().getId();
        UUID reliefCategoryId = receipt.getReliefCategory() == null
                ? null
                : receipt.getReliefCategory().getId();
        boolean deleteStoredFile = isStoredFile(receipt);

        receiptRepository.delete(receipt);
        syncClaim(userPolicyYearId, reliefCategoryId);

        if (deleteStoredFile) {
            tryDeleteStoredFile(receipt.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> getReceiptsForUserYear(Integer year) {
        UserPolicyYear userPolicyYear = findExistingUserPolicyYear(year);

        return receiptRepository.findAllDetailedByUserPolicyYearId(userPolicyYear.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ReceiptResponse uploadReceiptForUserYear(Integer year, UploadYearReceiptRequest request) {
        UserPolicyYear userPolicyYear = findExistingUserPolicyYear(year);
        AppUser currentUser = getCurrentUserEntity();
        ReliefCategory reliefCategory = resolveReliefCategory(
                request.getReliefCategoryId(),
                userPolicyYear.getPolicyYear(),
                currentUser
        );
        MultipartFile file = request.getFile();

        if (file == null || file.isEmpty()) {
            throw new CalculatorValidationException("Receipt file is required");
        }

        Receipt receipt = new Receipt(
                userPolicyYear,
                request.getMerchantName().trim(),
                request.getReceiptDate(),
                request.getAmount(),
                reliefCategory,
                normalizeOptionalString(request.getNotes()),
                determineStoredFileName(file, year),
                null
        );

        Receipt createdReceipt = receiptRepository.save(receipt);
        createdReceipt.update(
                userPolicyYear,
                request.getMerchantName().trim(),
                request.getReceiptDate(),
                request.getAmount(),
                reliefCategory,
                normalizeOptionalString(request.getNotes()),
                determineStoredFileName(file, year),
                buildStoredFileUrl(createdReceipt.getId())
        );

        storeUploadedFile(createdReceipt.getId(), file);
        syncClaim(userPolicyYear.getId(), reliefCategory);

        return toResponse(createdReceipt);
    }

    @Transactional(readOnly = true)
    public Resource loadReceiptFile(UUID id) {
        Receipt receipt = findReceiptForCurrentUser(id);

        if (!isStoredFile(receipt)) {
            throw new ResourceNotFoundException("Receipt file " + id + " not found");
        }

        try {
            return receiptStorageService.load(id);
        } catch (IOException exception) {
            throw new ResourceNotFoundException("Receipt file " + id + " not found");
        }
    }

    private Receipt findReceiptForCurrentUser(UUID id) {
        return receiptRepository.findDetailedByIdAndUserId(id, getCurrentUser().id())
                .orElseThrow(() -> new ResourceNotFoundException("Receipt " + id + " not found"));
    }

    private PolicyYear findPolicyYear(Integer year) {
        return policyYearRepository.findByYear(year)
                .orElseThrow(() -> new CalculatorValidationException(
                        "Policy year " + year + " not found"
                ));
    }

    private ReliefCategory resolveReliefCategory(UUID reliefCategoryId, PolicyYear policyYear, AppUser currentUser) {
        if (reliefCategoryId == null) {
            return null;
        }

        ReliefCategory reliefCategory = reliefCategoryRepository.findById(reliefCategoryId)
                .orElseThrow(() -> new CalculatorValidationException(
                        "Relief category " + reliefCategoryId + " not found"
                ));

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
                .orElseGet(() -> userPolicyYearRepository.save(
                        new UserPolicyYear(currentUser, findPolicyYear(year))
                ));
    }

    private UserPolicyYear findExistingUserPolicyYear(Integer year) {
        return userPolicyYearRepository.findByUserIdAndPolicyYearYear(getCurrentUser().id(), year)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Year workspace " + year + " not found for the current user"
                ));
    }

    private AppUser getCurrentUserEntity() {
        CurrentUser currentUser = getCurrentUser();
        return appUserRepository.findById(currentUser.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Current user " + currentUser.email() + " was not found"
                ));
    }

    private CurrentUser getCurrentUser() {
        return currentUserProvider.getCurrentUser();
    }

    private void syncClaim(UUID userPolicyYearId, UUID reliefCategoryId) {
        if (userPolicyYearId == null || reliefCategoryId == null) {
            return;
        }

        userReliefClaimSyncService.syncClaim(userPolicyYearId, reliefCategoryId);
    }

    private void syncClaim(UUID userPolicyYearId, ReliefCategory reliefCategory) {
        syncClaim(userPolicyYearId, reliefCategory == null ? null : reliefCategory.getId());
    }

    private String determineStoredFileName(MultipartFile file, Integer year) {
        String originalFilename = normalizeOptionalString(file.getOriginalFilename());

        if (originalFilename != null) {
            return originalFilename;
        }

        return "receipt-" + year + ".bin";
    }

    private String buildStoredFileUrl(UUID receiptId) {
        if (receiptId == null) {
            return null;
        }

        return "/api/receipts/" + receiptId + "/file";
    }

    private boolean isStoredFile(Receipt receipt) {
        return Objects.equals(receipt.getFileUrl(), buildStoredFileUrl(receipt.getId()));
    }

    private void storeUploadedFile(UUID receiptId, MultipartFile file) {
        try {
            receiptStorageService.store(receiptId, file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store uploaded receipt file", exception);
        }
    }

    private void tryDeleteStoredFile(UUID receiptId) {
        try {
            receiptStorageService.delete(receiptId);
        } catch (IOException ignored) {
        }
    }

    private ReceiptResponse toResponse(Receipt receipt) {
        ReliefCategory reliefCategory = receipt.getReliefCategory();

        return new ReceiptResponse(
                receipt.getId(),
                receipt.getPolicyYear(),
                receipt.getMerchantName(),
                receipt.getReceiptDate(),
                receipt.getAmount(),
                reliefCategory == null ? null : reliefCategory.getId(),
                reliefCategory == null ? null : reliefCategory.getName(),
                receipt.getNotes(),
                receipt.getFileName(),
                receipt.getFileUrl(),
                receipt.getCreatedAt(),
                receipt.getUpdatedAt()
        );
    }

    private String normalizeOptionalString(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
