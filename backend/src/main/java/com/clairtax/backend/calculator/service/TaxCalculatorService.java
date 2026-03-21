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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Profile("!local")
@Transactional(readOnly = true)
public class TaxCalculatorService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PolicyYearRepository policyYearRepository;
    private final ReliefCategoryRepository reliefCategoryRepository;
    private final TaxBracketRepository taxBracketRepository;

    public TaxCalculatorService(
            PolicyYearRepository policyYearRepository,
            ReliefCategoryRepository reliefCategoryRepository,
            TaxBracketRepository taxBracketRepository
    ) {
        this.policyYearRepository = policyYearRepository;
        this.reliefCategoryRepository = reliefCategoryRepository;
        this.taxBracketRepository = taxBracketRepository;
    }

    public CalculateTaxResponse calculate(CalculateTaxRequest request) {
        PolicyYear policyYear = policyYearRepository.findByYear(request.policyYear())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Policy year " + request.policyYear() + " not found"
                ));

        BigDecimal grossIncome = toMoney(request.grossIncome());
        Map<UUID, ReliefCategory> reliefCategories = loadReliefCategories(
                policyYear.getId(),
                request.policyYear(),
                request.selectedReliefs()
        );

        BigDecimal totalRelief = calculateTotalRelief(request.selectedReliefs(), reliefCategories);
        BigDecimal chargeableIncome = toMoney(grossIncome.subtract(totalRelief).max(BigDecimal.ZERO));

        List<TaxBracket> taxBrackets = taxBracketRepository.findAllByPolicyYearIdOrderByMinIncomeAsc(policyYear.getId());
        if (taxBrackets.isEmpty()) {
            throw new CalculatorValidationException(
                    "No tax brackets configured for policy year " + request.policyYear()
            );
        }

        List<TaxBreakdownResponse> taxBreakdown = calculateTaxBreakdown(chargeableIncome, taxBrackets);
        BigDecimal totalTaxPayable = taxBreakdown.stream()
                .map(TaxBreakdownResponse::taxForBracket)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new CalculateTaxResponse(
                request.policyYear(),
                grossIncome,
                totalRelief,
                chargeableIncome,
                taxBreakdown,
                totalTaxPayable
        );
    }

    private Map<UUID, ReliefCategory> loadReliefCategories(
            UUID policyYearId,
            Integer policyYear,
            List<ReliefClaimRequest> selectedReliefs
    ) {
        Set<UUID> requestedIds = selectedReliefs.stream()
                .map(ReliefClaimRequest::reliefCategoryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (requestedIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ReliefCategory> reliefCategories = reliefCategoryRepository
                .findAllByPolicyYearIdAndIdIn(policyYearId, requestedIds)
                .stream()
                .collect(Collectors.toMap(ReliefCategory::getId, Function.identity()));

        List<UUID> invalidIds = requestedIds.stream()
                .filter(requestedId -> !reliefCategories.containsKey(requestedId))
                .toList();

        if (!invalidIds.isEmpty()) {
            throw new CalculatorValidationException(
                    "Relief categories " + invalidIds + " do not belong to policy year " + policyYear
            );
        }

        return reliefCategories;
    }

    private BigDecimal calculateTotalRelief(
            List<ReliefClaimRequest> selectedReliefs,
            Map<UUID, ReliefCategory> reliefCategories
    ) {
        BigDecimal totalRelief = ZERO;
        for (ReliefClaimRequest selectedRelief : selectedReliefs) {
            ReliefCategory reliefCategory = reliefCategories.get(selectedRelief.reliefCategoryId());
            BigDecimal claimedAmount = toMoney(selectedRelief.claimedAmount());
            BigDecimal maxAllowed = toMoney(reliefCategory.getMaxAmount());
            totalRelief = totalRelief.add(claimedAmount.min(maxAllowed));
        }

        return totalRelief.setScale(2, RoundingMode.HALF_UP);
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
                taxBracket.getTaxRate(),
                taxableAmount,
                taxForBracket
        );
    }

    private BigDecimal calculateTaxableAmount(BigDecimal chargeableIncome, TaxBracket taxBracket) {
        BigDecimal upperBound = taxBracket.getMaxIncome() == null
                ? chargeableIncome
                : chargeableIncome.min(taxBracket.getMaxIncome());
        BigDecimal lowerThresholdExclusive = taxBracket.getMinIncome().compareTo(BigDecimal.ZERO) > 0
                ? taxBracket.getMinIncome().subtract(ONE_CENT)
                : BigDecimal.ZERO;

        if (upperBound.compareTo(lowerThresholdExclusive) <= 0) {
            return ZERO;
        }

        return toMoney(upperBound.subtract(lowerThresholdExclusive));
    }

    private BigDecimal toMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
