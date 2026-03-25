package com.clairtax.backend.reliefclaim.service;

import com.clairtax.backend.calculator.entity.ReliefCategory;
import com.clairtax.backend.calculator.repository.ReliefCategoryRepository;
import com.clairtax.backend.receipt.repository.ReceiptRepository;
import com.clairtax.backend.reliefclaim.entity.UserReliefClaim;
import com.clairtax.backend.reliefclaim.repository.UserReliefClaimRepository;
import com.clairtax.backend.useryear.entity.UserPolicyYear;
import com.clairtax.backend.useryear.repository.UserPolicyYearRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Profile("!local")
@Transactional
public class UserReliefClaimSyncService {

    private final UserReliefClaimRepository userReliefClaimRepository;
    private final UserPolicyYearRepository userPolicyYearRepository;
    private final ReliefCategoryRepository reliefCategoryRepository;
    private final ReceiptRepository receiptRepository;

    public UserReliefClaimSyncService(
            UserReliefClaimRepository userReliefClaimRepository,
            UserPolicyYearRepository userPolicyYearRepository,
            ReliefCategoryRepository reliefCategoryRepository,
            ReceiptRepository receiptRepository
    ) {
        this.userReliefClaimRepository = userReliefClaimRepository;
        this.userPolicyYearRepository = userPolicyYearRepository;
        this.reliefCategoryRepository = reliefCategoryRepository;
        this.receiptRepository = receiptRepository;
    }

    public void syncClaim(UUID userPolicyYearId, UUID reliefCategoryId) {
        if (userPolicyYearId == null || reliefCategoryId == null) {
            return;
        }

        BigDecimal claimedAmount = receiptRepository.sumAmountByUserPolicyYearIdAndReliefCategoryId(
                userPolicyYearId,
                reliefCategoryId
        );

        userReliefClaimRepository.findByUserPolicyYearIdAndReliefCategoryId(userPolicyYearId, reliefCategoryId)
                .ifPresentOrElse(existingClaim -> updateExistingClaim(existingClaim, claimedAmount),
                        () -> createClaimIfNeeded(userPolicyYearId, reliefCategoryId, claimedAmount));
    }

    private void updateExistingClaim(UserReliefClaim existingClaim, BigDecimal claimedAmount) {
        if (claimedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            userReliefClaimRepository.delete(existingClaim);
            return;
        }

        existingClaim.setClaimedAmount(claimedAmount);
    }

    private void createClaimIfNeeded(UUID userPolicyYearId, UUID reliefCategoryId, BigDecimal claimedAmount) {
        if (claimedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        UserPolicyYear userPolicyYear = userPolicyYearRepository.findById(userPolicyYearId)
                .orElseThrow(() -> new IllegalStateException(
                        "User policy year " + userPolicyYearId + " was not found"
                ));
        ReliefCategory reliefCategory = reliefCategoryRepository.findById(reliefCategoryId)
                .orElseThrow(() -> new IllegalStateException(
                        "Relief category " + reliefCategoryId + " was not found"
                ));

        userReliefClaimRepository.save(new UserReliefClaim(userPolicyYear, reliefCategory, claimedAmount));
    }
}
