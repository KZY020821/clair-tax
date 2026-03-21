package com.clairtax.backend.policy.service;

import com.clairtax.backend.calculator.entity.ReliefCategory;
import com.clairtax.backend.calculator.exception.ResourceNotFoundException;
import com.clairtax.backend.calculator.repository.ReliefCategoryRepository;
import com.clairtax.backend.policy.dto.PolicyReliefCategoryResponse;
import com.clairtax.backend.policy.dto.PolicyResponse;
import com.clairtax.backend.policyyear.entity.PolicyYear;
import com.clairtax.backend.policyyear.repository.PolicyYearRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("!local")
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
                .findAllByPolicyYearIdOrderByNameAsc(policyYear.getId())
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
                reliefCategory.getName(),
                reliefCategory.getDescription(),
                reliefCategory.getMaxAmount(),
                reliefCategory.isRequiresReceipt()
        );
    }
}
