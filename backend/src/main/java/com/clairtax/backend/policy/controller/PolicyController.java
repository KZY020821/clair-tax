package com.clairtax.backend.policy.controller;

import com.clairtax.backend.policy.dto.PolicyResponse;
import com.clairtax.backend.policy.service.PolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/{year}")
    public PolicyResponse getPolicy(@PathVariable Integer year) {
        return policyService.getPolicy(year);
    }
}
