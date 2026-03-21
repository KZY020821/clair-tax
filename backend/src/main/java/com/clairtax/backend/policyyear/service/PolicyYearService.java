package com.clairtax.backend.policyyear.service;

import com.clairtax.backend.policyyear.dto.PolicyYearResponse;
import com.clairtax.backend.policyyear.repository.PolicyYearRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("!local")
public class PolicyYearService {

    private final PolicyYearRepository policyYearRepository;

    public PolicyYearService(PolicyYearRepository policyYearRepository) {
        this.policyYearRepository = policyYearRepository;
    }

    public List<PolicyYearResponse> getPolicyYears() {
        return policyYearRepository.findAllByOrderByYearDesc().stream()
                .map(policyYear -> new PolicyYearResponse(
                        policyYear.getId(),
                        policyYear.getYear(),
                        policyYear.getStatus(),
                        policyYear.getCreatedAt()
                ))
                .toList();
    }
}
