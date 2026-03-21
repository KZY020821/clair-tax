package com.clairtax.backend.policyyear.repository;

import com.clairtax.backend.policyyear.entity.PolicyYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyYearRepository extends JpaRepository<PolicyYear, UUID> {

    List<PolicyYear> findAllByOrderByYearDesc();

    Optional<PolicyYear> findByYear(Integer year);
}
