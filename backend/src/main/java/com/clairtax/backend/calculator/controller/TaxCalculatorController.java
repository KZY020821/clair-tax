package com.clairtax.backend.calculator.controller;

import com.clairtax.backend.calculator.dto.CalculateTaxRequest;
import com.clairtax.backend.calculator.dto.CalculateTaxResponse;
import com.clairtax.backend.calculator.service.TaxCalculatorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calculator")
public class TaxCalculatorController {

    private final TaxCalculatorService taxCalculatorService;

    public TaxCalculatorController(TaxCalculatorService taxCalculatorService) {
        this.taxCalculatorService = taxCalculatorService;
    }

    @PostMapping("/calculate")
    public CalculateTaxResponse calculate(@Valid @RequestBody CalculateTaxRequest request) {
        return taxCalculatorService.calculate(request);
    }
}
