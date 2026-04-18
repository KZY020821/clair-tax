package com.clairtax.backend.suggestion.service;

import com.clairtax.backend.calculator.entity.ReliefCategory;
import com.clairtax.backend.calculator.exception.ResourceNotFoundException;
import com.clairtax.backend.calculator.repository.ReliefCategoryRepository;
import com.clairtax.backend.policyyear.entity.PolicyYear;
import com.clairtax.backend.policyyear.repository.PolicyYearRepository;
import com.clairtax.backend.receipt.entity.Receipt;
import com.clairtax.backend.receipt.model.ReceiptStatus;
import com.clairtax.backend.receipt.repository.ReceiptRepository;
import com.clairtax.backend.reliefclaim.entity.UserReliefClaim;
import com.clairtax.backend.reliefclaim.repository.UserReliefClaimRepository;
import com.clairtax.backend.suggestion.dto.SuggestionResponse;
import com.clairtax.backend.suggestion.model.SuggestionPriority;
import com.clairtax.backend.suggestion.model.SuggestionType;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import com.clairtax.backend.user.service.CurrentUser;
import com.clairtax.backend.user.service.CurrentUserProvider;
import com.clairtax.backend.user.service.ProfileReliefResolver;
import com.clairtax.backend.useryear.entity.UserPolicyYear;
import com.clairtax.backend.useryear.repository.UserPolicyYearRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SuggestionGenerationService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HIGH_PRIORITY_AMOUNT_THRESHOLD = new BigDecimal("1000.00");
    private static final BigDecimal MEDIUM_PRIORITY_AMOUNT_THRESHOLD = new BigDecimal("300.00");

    private static final String DISABLED_INDIVIDUAL_CODE = "disabled_individual";
    private static final String SPOUSE_RELIEF_CODE = "spouse_relief";
    private static final String DISABLED_SPOUSE_CODE = "disabled_spouse";

    private final ProfileReliefResolver profileReliefResolver;
    private final ReceiptRepository receiptRepository;
    private final UserReliefClaimRepository userReliefClaimRepository;
    private final ReliefCategoryRepository reliefCategoryRepository;
    private final UserPolicyYearRepository userPolicyYearRepository;
    private final PolicyYearRepository policyYearRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public SuggestionGenerationService(
            ProfileReliefResolver profileReliefResolver,
            ReceiptRepository receiptRepository,
            UserReliefClaimRepository userReliefClaimRepository,
            ReliefCategoryRepository reliefCategoryRepository,
            UserPolicyYearRepository userPolicyYearRepository,
            PolicyYearRepository policyYearRepository,
            CurrentUserProvider currentUserProvider,
            AppUserRepository appUserRepository
    ) {
        this.profileReliefResolver = profileReliefResolver;
        this.receiptRepository = receiptRepository;
        this.userReliefClaimRepository = userReliefClaimRepository;
        this.reliefCategoryRepository = reliefCategoryRepository;
        this.userPolicyYearRepository = userPolicyYearRepository;
        this.policyYearRepository = policyYearRepository;
        this.currentUserProvider = currentUserProvider;
        this.appUserRepository = appUserRepository;
    }

    public List<SuggestionResponse> getSuggestionsForCurrentUser(Integer policyYear) {
        AppUser currentUser = getCurrentUserEntity();
        return generateSuggestions(currentUser, policyYear);
    }

    public List<SuggestionResponse> generateSuggestions(AppUser user, Integer policyYear) {
        PolicyYear policyYearEntity = policyYearRepository.findByYear(policyYear)
                .orElseThrow(() -> new ResourceNotFoundException("Policy year " + policyYear + " not found"));

        Optional<UserPolicyYear> userPolicyYearOpt = userPolicyYearRepository
                .findByUserIdAndPolicyYearYear(user.getId(), policyYear);

        List<ReliefCategory> allCategories = reliefCategoryRepository
                .findAllByPolicyYearIdOrderByDisplayOrderAscNameAsc(policyYearEntity.getId());

        List<ReliefCategory> eligibleCategories = profileReliefResolver
                .filterVisibleCategories(user, allCategories);

        Map<UUID, UserReliefClaim> currentClaims = new HashMap<>();
        if (userPolicyYearOpt.isPresent()) {
            currentClaims = userReliefClaimRepository
                    .findAllByUserPolicyYearId(userPolicyYearOpt.get().getId())
                    .stream()
                    .collect(Collectors.toMap(
                            claim -> claim.getReliefCategory().getId(),
                            claim -> claim
                    ));
        }

        List<SuggestionResponse> suggestions = new ArrayList<>();

        if (userPolicyYearOpt.isPresent()) {
            suggestions.addAll(generateReceiptBasedSuggestions(
                    userPolicyYearOpt.get(),
                    eligibleCategories,
                    currentClaims
            ));
        }

        suggestions.addAll(generateProfileBasedSuggestions(
                user,
                eligibleCategories,
                currentClaims
        ));

        suggestions.sort((s1, s2) -> {
            int priorityCompare = s1.priority().compareTo(s2.priority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }

            int additionalAmountCompare = getAdditionalClaimableAmount(s2)
                    .compareTo(getAdditionalClaimableAmount(s1));
            if (additionalAmountCompare != 0) {
                return additionalAmountCompare;
            }

            int supportingReceiptCompare = Integer.compare(
                    s2.supportingReceiptIds().size(),
                    s1.supportingReceiptIds().size()
            );
            if (supportingReceiptCompare != 0) {
                return supportingReceiptCompare;
            }

            return s1.reliefCategoryName().compareToIgnoreCase(s2.reliefCategoryName());
        });

        return suggestions.stream().limit(10).collect(Collectors.toList());
    }

    private List<SuggestionResponse> generateReceiptBasedSuggestions(
            UserPolicyYear userPolicyYear,
            List<ReliefCategory> eligibleCategories,
            Map<UUID, UserReliefClaim> currentClaims
    ) {
        List<SuggestionResponse> suggestions = new ArrayList<>();
        List<Receipt> verifiedReceipts = receiptRepository
                .findAllDetailedByUserPolicyYearId(userPolicyYear.getId())
                .stream()
                .filter(receipt -> receipt.getStatus() == ReceiptStatus.VERIFIED)
                .filter(receipt -> receipt.getReliefCategory() != null)
                .filter(receipt -> receipt.getAmount() != null)
                .toList();

        for (ReliefCategory category : eligibleCategories) {
            if (isServerOwnedFixedCode(category.getCode())) {
                continue;
            }

            BigDecimal verifiedReceiptTotal = receiptRepository
                    .sumAmountByUserPolicyYearIdAndReliefCategoryId(
                            userPolicyYear.getId(),
                            category.getId(),
                            ReceiptStatus.VERIFIED
                    );

            if (verifiedReceiptTotal.compareTo(ZERO) <= 0) {
                continue;
            }

            UserReliefClaim currentClaim = currentClaims.get(category.getId());
            BigDecimal currentClaimedAmount = currentClaim != null
                    ? currentClaim.getClaimedAmount()
                    : ZERO;

            BigDecimal suggestedAmount = verifiedReceiptTotal.min(category.getMaxAmount());
            BigDecimal additionalClaimableAmount = suggestedAmount.subtract(currentClaimedAmount);

            if (additionalClaimableAmount.compareTo(ZERO) <= 0) {
                continue;
            }

            List<UUID> supportingReceiptIds = verifiedReceipts.stream()
                    .filter(receipt -> receipt.getReliefCategory().getId().equals(category.getId()))
                    .map(Receipt::getId)
                    .toList();

            String reason;
            SuggestionType suggestionType;
            if (currentClaimedAmount.compareTo(ZERO) > 0) {
                reason = String.format(
                        "You have %d verified receipt%s totaling RM %.2f and can still add RM %.2f to this claim",
                        supportingReceiptIds.size(),
                        supportingReceiptIds.size() == 1 ? "" : "s",
                        verifiedReceiptTotal,
                        additionalClaimableAmount
                );
                suggestionType = SuggestionType.UNDER_CLAIMED;
            } else {
                reason = String.format(
                        "You have %d verified receipt%s totaling RM %.2f for this category",
                        supportingReceiptIds.size(),
                        supportingReceiptIds.size() == 1 ? "" : "s",
                        verifiedReceiptTotal
                );
                suggestionType = SuggestionType.RECEIPT_BASED;
            }

            SuggestionPriority priority = determinePriority(additionalClaimableAmount);

            suggestions.add(new SuggestionResponse(
                    UUID.randomUUID(),
                    category.getId(),
                    category.getName(),
                    category.getCode(),
                    suggestedAmount,
                    currentClaimedAmount,
                    reason,
                    supportingReceiptIds,
                    priority,
                    suggestionType
            ));
        }

        return suggestions;
    }

    private List<SuggestionResponse> generateProfileBasedSuggestions(
            AppUser user,
            List<ReliefCategory> eligibleCategories,
            Map<UUID, UserReliefClaim> currentClaims
    ) {
        List<SuggestionResponse> suggestions = new ArrayList<>();

        for (ReliefCategory category : eligibleCategories) {
            if (!isServerOwnedFixedCode(category.getCode())) {
                continue;
            }

            UserReliefClaim currentClaim = currentClaims.get(category.getId());
            if (currentClaim != null && currentClaim.getClaimedAmount().compareTo(ZERO) > 0) {
                continue;
            }

            BigDecimal suggestedAmount = category.getUnitAmount() != null
                    ? category.getUnitAmount()
                    : category.getMaxAmount();

            String reason = buildProfileBasedReason(user, category.getCode());
            SuggestionPriority priority = determinePriority(suggestedAmount);

            suggestions.add(new SuggestionResponse(
                    UUID.randomUUID(),
                    category.getId(),
                    category.getName(),
                    category.getCode(),
                    suggestedAmount,
                    ZERO,
                    reason,
                    List.of(),
                    priority,
                    SuggestionType.PROFILE_BASED
            ));
        }

        return suggestions;
    }

    private SuggestionPriority determinePriority(BigDecimal claimableAmount) {
        if (claimableAmount.compareTo(HIGH_PRIORITY_AMOUNT_THRESHOLD) >= 0) {
            return SuggestionPriority.HIGH;
        } else if (claimableAmount.compareTo(MEDIUM_PRIORITY_AMOUNT_THRESHOLD) >= 0) {
            return SuggestionPriority.MEDIUM;
        } else {
            return SuggestionPriority.LOW;
        }
    }

    private BigDecimal getAdditionalClaimableAmount(SuggestionResponse suggestion) {
        return suggestion.suggestedAmount().subtract(suggestion.currentClaimedAmount());
    }

    private String buildProfileBasedReason(AppUser user, String categoryCode) {
        return switch (categoryCode) {
            case DISABLED_INDIVIDUAL_CODE ->
                    "You are registered as disabled, making you eligible for additional personal relief";
            case SPOUSE_RELIEF_CODE ->
                    "You are married with a non-working spouse, making you eligible for spousal relief";
            case DISABLED_SPOUSE_CODE ->
                    "Your non-working spouse is disabled, making you eligible for additional spousal relief";
            default -> "You are eligible for this relief based on your profile";
        };
    }

    private boolean isServerOwnedFixedCode(String code) {
        return DISABLED_INDIVIDUAL_CODE.equals(code) ||
                SPOUSE_RELIEF_CODE.equals(code) ||
                DISABLED_SPOUSE_CODE.equals(code);
    }

    private AppUser getCurrentUserEntity() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        return appUserRepository.findById(currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Current user " + currentUser.email() + " was not found"
                ));
    }
}
