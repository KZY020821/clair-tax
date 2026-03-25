package com.clairtax.backend.calculator.service;

import com.clairtax.backend.calculator.dto.CalculateTaxRequest;
import com.clairtax.backend.calculator.dto.CalculateTaxResponse;
import com.clairtax.backend.calculator.dto.ReliefClaimRequest;
import com.clairtax.backend.calculator.dto.TaxBreakdownResponse;
import com.clairtax.backend.calculator.entity.ReliefCategory;
import com.clairtax.backend.calculator.entity.TaxBracket;
import com.clairtax.backend.calculator.exception.CalculatorValidationException;
import com.clairtax.backend.calculator.exception.ResourceNotFoundException;
import com.clairtax.backend.calculator.repository.ReliefCategoryRepository;
import com.clairtax.backend.calculator.repository.TaxBracketRepository;
import com.clairtax.backend.policyyear.entity.PolicyYear;
import com.clairtax.backend.policyyear.repository.PolicyYearRepository;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import com.clairtax.backend.user.service.CurrentUser;
import com.clairtax.backend.user.service.CurrentUserProvider;
import com.clairtax.backend.user.service.ProfileReliefResolver;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("!local")
@Transactional(readOnly = true)
public class TaxCalculatorService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal REBATE_LIMIT = new BigDecimal("35000.00");
    private static final BigDecimal INDIVIDUAL_REBATE = new BigDecimal("400.00");

    private final PolicyYearRepository policyYearRepository;
    private final ReliefCategoryRepository reliefCategoryRepository;
    private final TaxBracketRepository taxBracketRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;
    private final ProfileReliefResolver profileReliefResolver;

    public TaxCalculatorService(
            PolicyYearRepository policyYearRepository,
            ReliefCategoryRepository reliefCategoryRepository,
            TaxBracketRepository taxBracketRepository,
            CurrentUserProvider currentUserProvider,
            AppUserRepository appUserRepository,
            ProfileReliefResolver profileReliefResolver
    ) {
        this.policyYearRepository = policyYearRepository;
        this.reliefCategoryRepository = reliefCategoryRepository;
        this.taxBracketRepository = taxBracketRepository;
        this.currentUserProvider = currentUserProvider;
        this.appUserRepository = appUserRepository;
        this.profileReliefResolver = profileReliefResolver;
    }

    public CalculateTaxResponse calculate(CalculateTaxRequest request) {
        PolicyYear policyYear = policyYearRepository.findByYear(request.policyYear())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Policy year " + request.policyYear() + " not found"
                ));

        BigDecimal grossIncome = toMoney(request.grossIncome());
        BigDecimal zakat = toMoney(request.zakat());

        List<ReliefCategory> reliefCategories = reliefCategoryRepository
                .findAllByPolicyYearIdOrderByDisplayOrderAscNameAsc(policyYear.getId());
        AppUser currentUser = getCurrentUserEntity();

        if (reliefCategories.isEmpty()) {
            throw new CalculatorValidationException(
                    "No relief categories configured for policy year " + request.policyYear()
            );
        }

        Map<UUID, ReliefCategory> reliefCategoriesById = toReliefCategoryMap(
                reliefCategories,
                currentUser,
                request.policyYear(),
                request.selectedReliefs()
        );
        List<ReliefCategory> visibleReliefCategories = reliefCategories.stream()
                .filter(category -> reliefCategoriesById.containsKey(category.getId()))
                .toList();
        Map<String, BigDecimal> activeReliefAmounts = calculateActiveReliefAmounts(
                visibleReliefCategories,
                request.selectedReliefs()
        );
        activeReliefAmounts.putAll(
                profileReliefResolver.resolveProfileDrivenAmounts(currentUser, visibleReliefCategories)
        );

        BigDecimal totalRelief = calculateTotalRelief(visibleReliefCategories, activeReliefAmounts);
        BigDecimal chargeableIncome = toMoney(grossIncome.subtract(totalRelief).max(ZERO));

        List<TaxBracket> taxBrackets = taxBracketRepository.findAllByPolicyYearIdOrderByMinIncomeAsc(policyYear.getId());
        if (taxBrackets.isEmpty()) {
            throw new CalculatorValidationException(
                    "No tax brackets configured for policy year " + request.policyYear()
            );
        }

        List<TaxBreakdownResponse> taxBreakdown = calculateTaxBreakdown(chargeableIncome, taxBrackets);
        BigDecimal taxAmount = taxBreakdown.stream()
                .map(TaxBreakdownResponse::taxForBracket)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal taxRebate = calculateTaxRebate(chargeableIncome, activeReliefAmounts);
        BigDecimal taxYouShouldPay = toMoney(taxAmount.subtract(taxRebate).subtract(zakat).max(ZERO));

        return new CalculateTaxResponse(
                request.policyYear(),
                grossIncome,
                totalRelief,
                chargeableIncome,
                chargeableIncome,
                taxBreakdown,
                toMoney(taxAmount),
                taxRebate,
                zakat,
                taxYouShouldPay,
                taxYouShouldPay
        );
    }

    private Map<UUID, ReliefCategory> toReliefCategoryMap(
            List<ReliefCategory> reliefCategories,
            AppUser currentUser,
            Integer policyYear,
            List<ReliefClaimRequest> selectedReliefs
    ) {
        Map<UUID, ReliefCategory> allReliefCategoriesById = reliefCategories.stream()
                .collect(LinkedHashMap::new, (map, category) -> map.put(category.getId(), category), Map::putAll);
        Map<UUID, ReliefCategory> visibleReliefCategoriesById = reliefCategories.stream()
                .filter(category -> profileReliefResolver.isCategoryVisible(currentUser, category))
                .collect(LinkedHashMap::new, (map, category) -> map.put(category.getId(), category), Map::putAll);

        for (ReliefClaimRequest selectedRelief : selectedReliefs) {
            ReliefCategory selectedCategory = allReliefCategoriesById.get(selectedRelief.reliefCategoryId());
            if (selectedCategory == null) {
                throw new CalculatorValidationException(
                        "Relief category " + selectedRelief.reliefCategoryId()
                                + " does not belong to policy year " + policyYear
                );
            }

            if (!visibleReliefCategoriesById.containsKey(selectedRelief.reliefCategoryId())) {
                throw new CalculatorValidationException(
                        "Relief category " + selectedCategory.getName()
                                + " is not available for the saved profile"
                );
            }
        }

        return visibleReliefCategoriesById;
    }

    private Map<String, BigDecimal> calculateActiveReliefAmounts(
            List<ReliefCategory> reliefCategories,
            List<ReliefClaimRequest> selectedReliefs
    ) {
        Map<UUID, ReliefClaimRequest> selectedReliefsById = new LinkedHashMap<>();
        for (ReliefClaimRequest selectedRelief : selectedReliefs) {
            ReliefClaimRequest previous = selectedReliefsById.putIfAbsent(
                    selectedRelief.reliefCategoryId(),
                    selectedRelief
            );

            if (previous != null) {
                throw new CalculatorValidationException(
                        "Relief category " + selectedRelief.reliefCategoryId() + " was submitted more than once"
                );
            }
        }

        Map<String, BigDecimal> activeReliefAmounts = new LinkedHashMap<>();
        Map<String, Integer> exclusiveSelections = new LinkedHashMap<>();

        for (ReliefCategory reliefCategory : reliefCategories) {
            if (profileReliefResolver.isServerOwnedFixedCode(reliefCategory.getCode())) {
                continue;
            }

            ReliefClaimRequest selectedRelief = selectedReliefsById.get(reliefCategory.getId());
            BigDecimal rawReliefAmount = calculateRawReliefAmount(reliefCategory, selectedRelief);

            if (rawReliefAmount.compareTo(ZERO) > 0) {
                activeReliefAmounts.put(reliefCategory.getCode(), rawReliefAmount);
            }

            if (reliefCategory.getExclusiveGroupCode() != null && rawReliefAmount.compareTo(ZERO) > 0) {
                exclusiveSelections.merge(reliefCategory.getExclusiveGroupCode(), 1, Integer::sum);
            }
        }

        validateRequiredCategories(reliefCategories, activeReliefAmounts);
        validateExclusiveSelections(exclusiveSelections);

        return activeReliefAmounts;
    }

    private void validateRequiredCategories(
            List<ReliefCategory> reliefCategories,
            Map<String, BigDecimal> activeReliefAmounts
    ) {
        for (ReliefCategory reliefCategory : reliefCategories) {
            if (reliefCategory.getRequiresCategoryCode() == null) {
                continue;
            }

            BigDecimal selectedAmount = activeReliefAmounts.getOrDefault(reliefCategory.getCode(), ZERO);
            if (selectedAmount.compareTo(ZERO) <= 0) {
                continue;
            }

            BigDecimal requiredAmount = activeReliefAmounts.getOrDefault(
                    reliefCategory.getRequiresCategoryCode(),
                    ZERO
            );
            if (requiredAmount.compareTo(ZERO) <= 0) {
                throw new CalculatorValidationException(
                        "Relief category " + reliefCategory.getName()
                                + " requires " + reliefCategory.getRequiresCategoryCode()
                );
            }
        }
    }

    private void validateExclusiveSelections(Map<String, Integer> exclusiveSelections) {
        exclusiveSelections.forEach((exclusiveGroup, selectedCount) -> {
            if (selectedCount > 1) {
                throw new CalculatorValidationException(
                        "Only one option can be selected for " + exclusiveGroup
                );
            }
        });
    }

    private BigDecimal calculateRawReliefAmount(
            ReliefCategory reliefCategory,
            ReliefClaimRequest selectedRelief
    ) {
        return switch (reliefCategory.getInputType()) {
            case "fixed" -> calculateFixedRelief(reliefCategory, selectedRelief);
            case "count" -> calculateCountRelief(reliefCategory, selectedRelief);
            default -> calculateAmountRelief(reliefCategory, selectedRelief);
        };
    }

    private BigDecimal calculateFixedRelief(ReliefCategory reliefCategory, ReliefClaimRequest selectedRelief) {
        boolean selected = reliefCategory.isAutoApply()
                || (selectedRelief != null && Boolean.TRUE.equals(selectedRelief.selected()));

        if (!selected) {
            return ZERO;
        }

        BigDecimal fixedAmount = reliefCategory.getUnitAmount() != null
                ? reliefCategory.getUnitAmount()
                : reliefCategory.getMaxAmount();

        return toMoney(fixedAmount);
    }

    private BigDecimal calculateCountRelief(ReliefCategory reliefCategory, ReliefClaimRequest selectedRelief) {
        if (selectedRelief == null || selectedRelief.quantity() == null || selectedRelief.quantity() == 0) {
            return ZERO;
        }

        if (reliefCategory.getUnitAmount() == null) {
            throw new CalculatorValidationException(
                    "Relief category " + reliefCategory.getName() + " is missing its unit amount"
            );
        }

        if (reliefCategory.getMaxQuantity() != null
                && selectedRelief.quantity() > reliefCategory.getMaxQuantity()) {
            throw new CalculatorValidationException(
                    "Relief category " + reliefCategory.getName()
                            + " cannot exceed quantity " + reliefCategory.getMaxQuantity()
            );
        }

        return toMoney(reliefCategory.getUnitAmount().multiply(BigDecimal.valueOf(selectedRelief.quantity())));
    }

    private BigDecimal calculateAmountRelief(ReliefCategory reliefCategory, ReliefClaimRequest selectedRelief) {
        if (selectedRelief == null || selectedRelief.claimedAmount() == null) {
            return ZERO;
        }

        BigDecimal claimedAmount = toMoney(selectedRelief.claimedAmount());
        return claimedAmount.min(toMoney(reliefCategory.getMaxAmount()));
    }

    private BigDecimal calculateTotalRelief(
            List<ReliefCategory> reliefCategories,
            Map<String, BigDecimal> activeReliefAmounts
    ) {
        BigDecimal totalRelief = ZERO;
        Map<String, BigDecimal> groupedReliefAmounts = new LinkedHashMap<>();
        Map<String, BigDecimal> groupCaps = new LinkedHashMap<>();

        for (ReliefCategory reliefCategory : reliefCategories) {
            BigDecimal reliefAmount = activeReliefAmounts.getOrDefault(reliefCategory.getCode(), ZERO);
            if (reliefAmount.compareTo(ZERO) <= 0) {
                continue;
            }

            if (reliefCategory.getGroupCode() == null) {
                totalRelief = totalRelief.add(reliefAmount);
                continue;
            }

            groupedReliefAmounts.merge(reliefCategory.getGroupCode(), reliefAmount, BigDecimal::add);
            if (reliefCategory.getGroupMaxAmount() != null) {
                groupCaps.putIfAbsent(reliefCategory.getGroupCode(), reliefCategory.getGroupMaxAmount());
            }
        }

        for (Map.Entry<String, BigDecimal> groupedRelief : groupedReliefAmounts.entrySet()) {
            BigDecimal groupCap = groupCaps.get(groupedRelief.getKey());
            BigDecimal groupedAmount = groupedRelief.getValue();
            totalRelief = totalRelief.add(groupCap == null ? groupedAmount : groupedAmount.min(groupCap));
        }

        return toMoney(totalRelief);
    }

    private BigDecimal calculateTaxRebate(
            BigDecimal chargeableIncome,
            Map<String, BigDecimal> activeReliefAmounts
    ) {
        if (chargeableIncome.compareTo(REBATE_LIMIT) > 0) {
            return ZERO;
        }

        BigDecimal taxRebate = INDIVIDUAL_REBATE;
        if (activeReliefAmounts.getOrDefault("spouse_relief", ZERO).compareTo(ZERO) > 0) {
            taxRebate = taxRebate.add(INDIVIDUAL_REBATE);
        }

        return toMoney(taxRebate);
    }

    private List<TaxBreakdownResponse> calculateTaxBreakdown(
            BigDecimal chargeableIncome,
            List<TaxBracket> taxBrackets
    ) {
        return taxBrackets.stream()
                .map(taxBracket -> toTaxBreakdownResponse(chargeableIncome, taxBracket))
                .toList();
    }

    private TaxBreakdownResponse toTaxBreakdownResponse(BigDecimal chargeableIncome, TaxBracket taxBracket) {
        BigDecimal taxableAmount = calculateTaxableAmount(chargeableIncome, taxBracket);
        BigDecimal taxForBracket = taxableAmount.multiply(taxBracket.getTaxRate())
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        return new TaxBreakdownResponse(
                toMoney(taxBracket.getMinIncome()),
                taxBracket.getMaxIncome() == null ? null : toMoney(taxBracket.getMaxIncome()),
                toMoney(taxBracket.getTaxRate()),
                taxableAmount,
                toMoney(taxForBracket)
        );
    }

    private BigDecimal calculateTaxableAmount(BigDecimal chargeableIncome, TaxBracket taxBracket) {
        BigDecimal upperBound = taxBracket.getMaxIncome() == null
                ? chargeableIncome
                : chargeableIncome.min(taxBracket.getMaxIncome());

        if (upperBound.compareTo(taxBracket.getMinIncome()) <= 0) {
            return ZERO;
        }

        return toMoney(upperBound.subtract(taxBracket.getMinIncome()));
    }

    private BigDecimal toMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private AppUser getCurrentUserEntity() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        return appUserRepository.findById(currentUser.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Current user " + currentUser.email() + " was not found"
                ));
    }
}
