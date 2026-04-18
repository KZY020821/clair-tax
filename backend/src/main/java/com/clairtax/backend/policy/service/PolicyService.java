package com.clairtax.backend.policy.service;

import com.clairtax.backend.calculator.entity.ReliefCategory;
import com.clairtax.backend.calculator.exception.ResourceNotFoundException;
import com.clairtax.backend.calculator.repository.ReliefCategoryRepository;
import com.clairtax.backend.policy.dto.PolicyReliefCategoryResponse;
import com.clairtax.backend.policy.dto.PolicyResponse;
import com.clairtax.backend.policyyear.entity.PolicyYear;
import com.clairtax.backend.policyyear.repository.PolicyYearRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PolicyService {

    private final PolicyYearRepository policyYearRepository;
    private final ReliefCategoryRepository reliefCategoryRepository;

    public PolicyService(
            PolicyYearRepository policyYearRepository,
            ReliefCategoryRepository reliefCategoryRepository
    ) {
        this.policyYearRepository = policyYearRepository;
        this.reliefCategoryRepository = reliefCategoryRepository;
    }

    public PolicyResponse getPolicy(Integer year) {
        PolicyYear policyYear = policyYearRepository.findByYear(year)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Policy year " + year + " not found"
                ));

        List<PolicyReliefCategoryResponse> reliefCategories = reliefCategoryRepository
                .findAllByPolicyYearIdOrderByDisplayOrderAscNameAsc(policyYear.getId())
                .stream()
                .map(this::toReliefCategoryResponse)
                .toList();

        return new PolicyResponse(
                policyYear.getId(),
                policyYear.getYear(),
                policyYear.getStatus(),
                reliefCategories
        );
    }

    private PolicyReliefCategoryResponse toReliefCategoryResponse(ReliefCategory reliefCategory) {
        return new PolicyReliefCategoryResponse(
                reliefCategory.getId(),
                reliefCategory.getCode(),
                reliefCategory.getName(),
                reliefCategory.getDescription(),
                reliefCategory.getSection(),
                reliefCategory.getInputType(),
                reliefCategory.getUnitAmount(),
                reliefCategory.getMaxAmount(),
                reliefCategory.getMaxQuantity(),
                reliefCategory.getDisplayOrder(),
                reliefCategory.getGroupCode(),
                reliefCategory.getGroupMaxAmount(),
                reliefCategory.getExclusiveGroupCode(),
                reliefCategory.getRequiresCategoryCode(),
                reliefCategory.isAutoApply(),
                reliefCategory.isRequiresReceipt()
        );
    }
}
