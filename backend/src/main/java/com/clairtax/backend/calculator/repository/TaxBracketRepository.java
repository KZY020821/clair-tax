package com.clairtax.backend.calculator.repository;

import com.clairtax.backend.calculator.entity.TaxBracket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaxBracketRepository extends JpaRepository<TaxBracket, UUID> {

    List<TaxBracket> findAllByPolicyYearIdOrderByMinIncomeAsc(UUID policyYearId);
}
