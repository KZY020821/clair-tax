package com.clairtax.backend.policyyear.controller;

import com.clairtax.backend.policyyear.dto.PolicyYearResponse;
import com.clairtax.backend.policyyear.service.PolicyYearService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("!local")
@RequestMapping("/api/policy-years")
public class PolicyYearController {

    private final PolicyYearService policyYearService;

    public PolicyYearController(PolicyYearService policyYearService) {
        this.policyYearService = policyYearService;
    }

    @GetMapping
    public List<PolicyYearResponse> getPolicyYears() {
        return policyYearService.getPolicyYears();
    }
}
