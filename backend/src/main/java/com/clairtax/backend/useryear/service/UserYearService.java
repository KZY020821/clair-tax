package com.clairtax.backend.useryear.service;

import com.clairtax.backend.calculator.entity.ReliefCategory;
import com.clairtax.backend.calculator.exception.CalculatorValidationException;
import com.clairtax.backend.calculator.exception.ResourceNotFoundException;
import com.clairtax.backend.calculator.repository.ReliefCategoryRepository;
import com.clairtax.backend.policyyear.entity.PolicyYear;
import com.clairtax.backend.policyyear.repository.PolicyYearRepository;
import com.clairtax.backend.receipt.repository.ReceiptRepository;
import com.clairtax.backend.reliefclaim.entity.UserReliefClaim;
import com.clairtax.backend.reliefclaim.repository.UserReliefClaimRepository;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import com.clairtax.backend.user.service.CurrentUser;
import com.clairtax.backend.user.service.CurrentUserProvider;
import com.clairtax.backend.user.service.ProfileReliefResolver;
import com.clairtax.backend.useryear.dto.CreateUserYearRequest;
import com.clairtax.backend.useryear.dto.UserYearCategorySummaryResponse;
import com.clairtax.backend.useryear.dto.UserYearResponse;
import com.clairtax.backend.useryear.dto.UserYearWorkspaceResponse;
import com.clairtax.backend.useryear.entity.UserPolicyYear;
import com.clairtax.backend.useryear.repository.UserPolicyYearRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Profile("!local")
@Transactional
public class UserYearService {

    private final UserPolicyYearRepository userPolicyYearRepository;
    private final PolicyYearRepository policyYearRepository;
    private final ReliefCategoryRepository reliefCategoryRepository;
    private final UserReliefClaimRepository userReliefClaimRepository;
    private final ReceiptRepository receiptRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final ProfileReliefResolver profileReliefResolver;

    public UserYearService(
            UserPolicyYearRepository userPolicyYearRepository,
            PolicyYearRepository policyYearRepository,
            ReliefCategoryRepository reliefCategoryRepository,
            UserReliefClaimRepository userReliefClaimRepository,
            ReceiptRepository receiptRepository,
            CurrentUserProvider currentUserProvider,
            AppUserRepository appUserRepository,
            ProfileReliefResolver profileReliefResolver
    ) {
        this.userPolicyYearRepository = userPolicyYearRepository;
        this.policyYearRepository = policyYearRepository;
        this.reliefCategoryRepository = reliefCategoryRepository;
        this.userReliefClaimRepository = userReliefClaimRepository;
        this.receiptRepository = receiptRepository;
        this.currentUserProvider = currentUserProvider;
        this.appUserRepository = appUserRepository;
        this.profileReliefResolver = profileReliefResolver;
    }

    @Transactional(readOnly = true)
    public List<UserYearResponse> getUserYears() {
        return userPolicyYearRepository.findAllByUserIdOrderByPolicyYearYearDesc(getCurrentUser().id())
                .stream()
                .map(this::toUserYearResponse)
                .toList();
    }

    public UserYearResponse createUserYear(CreateUserYearRequest request) {
        CurrentUser currentUser = getCurrentUser();

        return userPolicyYearRepository.findByUserIdAndPolicyYearYear(currentUser.id(), request.policyYear())
                .map(this::toUserYearResponse)
                .orElseGet(() -> {
                    AppUser user = appUserRepository.findById(currentUser.id())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Current user " + currentUser.email() + " was not found"
                            ));
                    PolicyYear policyYear = policyYearRepository.findByYear(request.policyYear())
                            .orElseThrow(() -> new CalculatorValidationException(
                                    "Policy year " + request.policyYear() + " not found"
                            ));

                    return toUserYearResponse(userPolicyYearRepository.save(new UserPolicyYear(user, policyYear)));
                });
    }

    @Transactional(readOnly = true)
    public UserYearWorkspaceResponse getWorkspace(Integer year) {
        UserPolicyYear userPolicyYear = getUserPolicyYearForCurrentUser(year);
        AppUser currentUser = getCurrentUserEntity();
        List<ReliefCategory> reliefCategories = reliefCategoryRepository
                .findAllByPolicyYearIdOrderByDisplayOrderAscNameAsc(userPolicyYear.getPolicyYear().getId());
        List<ReliefCategory> visibleReliefCategories = profileReliefResolver.filterVisibleCategories(
                currentUser,
                reliefCategories
        );
        Map<UUID, UserReliefClaim> claimsByCategoryId = userReliefClaimRepository
                .findAllByUserPolicyYearId(userPolicyYear.getId())
                .stream()
                .collect(Collectors.toMap(claim -> claim.getReliefCategory().getId(), Function.identity()));
        Map<UUID, Long> receiptCountsByCategoryId = receiptRepository
                .countReceiptsByUserPolicyYearIdGroupedByReliefCategoryId(userPolicyYear.getId())
                .stream()
                .collect(Collectors.toMap(
                        ReceiptRepository.ReliefCategoryReceiptCount::getReliefCategoryId,
                        ReceiptRepository.ReliefCategoryReceiptCount::getReceiptCount
                ));
        Map<String, BigDecimal> profileDrivenAmounts = profileReliefResolver.resolveProfileDrivenAmounts(
                currentUser,
                visibleReliefCategories
        );

        List<UserYearCategorySummaryResponse> categorySummaries = visibleReliefCategories.stream()
                .map(category -> toCategorySummary(
                        category,
                        claimsByCategoryId,
                        receiptCountsByCategoryId,
                        profileDrivenAmounts.getOrDefault(category.getCode(), BigDecimal.ZERO)
                ))
                .toList();
        BigDecimal totalClaimedAmount = categorySummaries.stream()
                .map(UserYearCategorySummaryResponse::claimedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new UserYearWorkspaceResponse(
                userPolicyYear.getId(),
                userPolicyYear.getPolicyYear().getYear(),
                userPolicyYear.getPolicyYear().getStatus(),
                userPolicyYear.getCreatedAt(),
                userPolicyYear.getUpdatedAt(),
                categorySummaries.size(),
                categorySummaries.stream().mapToLong(UserYearCategorySummaryResponse::receiptCount).sum(),
                totalClaimedAmount,
                categorySummaries
        );
    }

    @Transactional(readOnly = true)
    public UserPolicyYear getUserPolicyYearForCurrentUser(Integer year) {
        return userPolicyYearRepository.findByUserIdAndPolicyYearYear(getCurrentUser().id(), year)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Year workspace " + year + " not found for the current user"
                ));
    }

    private UserYearCategorySummaryResponse toCategorySummary(
            ReliefCategory category,
            Map<UUID, UserReliefClaim> claimsByCategoryId,
            Map<UUID, Long> receiptCountsByCategoryId,
            BigDecimal profileDrivenAmount
    ) {
        BigDecimal receiptClaimedAmount = claimsByCategoryId.containsKey(category.getId())
                ? claimsByCategoryId.get(category.getId()).getClaimedAmount()
                : BigDecimal.ZERO;
        BigDecimal claimedAmount = receiptClaimedAmount.add(profileDrivenAmount);
        BigDecimal remainingAmount = category.getMaxAmount().subtract(claimedAmount);

        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
            remainingAmount = BigDecimal.ZERO;
        }

        return new UserYearCategorySummaryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription(),
                category.getSection(),
                category.getMaxAmount(),
                claimedAmount,
                remainingAmount,
                category.isRequiresReceipt(),
                receiptCountsByCategoryId.getOrDefault(category.getId(), 0L)
        );
    }

    private UserYearResponse toUserYearResponse(UserPolicyYear userPolicyYear) {
        return new UserYearResponse(
                userPolicyYear.getId(),
                userPolicyYear.getPolicyYear().getYear(),
                userPolicyYear.getPolicyYear().getStatus(),
                userPolicyYear.getCreatedAt(),
                userPolicyYear.getUpdatedAt()
        );
    }

    private CurrentUser getCurrentUser() {
        return currentUserProvider.getCurrentUser();
    }

    private AppUser getCurrentUserEntity() {
        CurrentUser currentUser = getCurrentUser();
        return appUserRepository.findById(currentUser.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Current user " + currentUser.email() + " was not found"
                ));
    }
}
