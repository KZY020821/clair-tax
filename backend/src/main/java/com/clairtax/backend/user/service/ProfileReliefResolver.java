package com.clairtax.backend.user.service;

import com.clairtax.backend.calculator.entity.ReliefCategory;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.entity.MaritalStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProfileReliefResolver {

    private static final String DISABLED_INDIVIDUAL_CODE = "disabled_individual";
    private static final String SPOUSE_RELIEF_CODE = "spouse_relief";
    private static final String DISABLED_SPOUSE_CODE = "disabled_spouse";

    private static final Set<String> PREVIOUSLY_MARRIED_CODES = Set.of("alimony_paid");
    private static final Set<String> CHILD_RELATED_CODES = Set.of(
            "breastfeeding_equipment",
            "childcare_fees",
            "sspn_net_savings",
            "child_below_18",
            "child_above_18_non_tertiary",
            "child_over_18_non_tertiary",
            "child_higher_education",
            "child_in_higher_education",
            "disabled_child",
            "disabled_child_higher_education",
            "disabled_child_in_higher_education",
            "child_learning_disability_support"
    );
    private static final Set<String> SERVER_OWNED_FIXED_CODES = Set.of(
            DISABLED_INDIVIDUAL_CODE,
            SPOUSE_RELIEF_CODE,
            DISABLED_SPOUSE_CODE
    );

    public List<ReliefCategory> filterVisibleCategories(AppUser user, List<ReliefCategory> categories) {
        return categories.stream()
                .filter(category -> isCategoryVisible(user, category))
                .toList();
    }

    public boolean isCategoryVisible(AppUser user, ReliefCategory category) {
        String code = category.getCode();

        if (DISABLED_INDIVIDUAL_CODE.equals(code)) {
            return user.isDisabled();
        }

        if (SPOUSE_RELIEF_CODE.equals(code)) {
            return user.getMaritalStatus() == MaritalStatus.MARRIED
                    && Boolean.FALSE.equals(user.getSpouseWorking());
        }

        if (DISABLED_SPOUSE_CODE.equals(code)) {
            return user.getMaritalStatus() == MaritalStatus.MARRIED
                    && Boolean.FALSE.equals(user.getSpouseWorking())
                    && Boolean.TRUE.equals(user.getSpouseDisabled());
        }

        if (PREVIOUSLY_MARRIED_CODES.contains(code)) {
            return user.getMaritalStatus() == MaritalStatus.PREVIOUSLY_MARRIED;
        }

        if (CHILD_RELATED_CODES.contains(code)) {
            return supportsChildRelatedReliefs(user);
        }

        return true;
    }

    public Map<String, BigDecimal> resolveProfileDrivenAmounts(AppUser user, List<ReliefCategory> categories) {
        Map<String, BigDecimal> activeAmounts = new LinkedHashMap<>();

        for (ReliefCategory category : categories) {
            if (!SERVER_OWNED_FIXED_CODES.contains(category.getCode())) {
                continue;
            }

            if (!isCategoryVisible(user, category)) {
                continue;
            }

            BigDecimal fixedAmount = category.getUnitAmount() != null
                    ? category.getUnitAmount()
                    : category.getMaxAmount();
            activeAmounts.put(category.getCode(), fixedAmount);
        }

        return activeAmounts;
    }

    public boolean isServerOwnedFixedCode(String code) {
        return SERVER_OWNED_FIXED_CODES.contains(code);
    }

    private boolean supportsChildRelatedReliefs(AppUser user) {
        if (user.getMaritalStatus() != MaritalStatus.MARRIED
                && user.getMaritalStatus() != MaritalStatus.PREVIOUSLY_MARRIED) {
            return false;
        }

        return Boolean.TRUE.equals(user.getHasChildren());
    }
}
