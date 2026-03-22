package com.clairtax.backend.calculator.repository;

import com.clairtax.backend.calculator.entity.ReliefCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReliefCategoryRepository extends JpaRepository<ReliefCategory, UUID> {

    List<ReliefCategory> findAllByPolicyYearIdAndIdIn(UUID policyYearId, Collection<UUID> ids);

    List<ReliefCategory> findAllByPolicyYearIdOrderByDisplayOrderAscNameAsc(UUID policyYearId);
}
