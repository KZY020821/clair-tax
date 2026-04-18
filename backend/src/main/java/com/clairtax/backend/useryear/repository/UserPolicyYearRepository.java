package com.clairtax.backend.useryear.repository;

import com.clairtax.backend.useryear.entity.UserPolicyYear;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPolicyYearRepository extends JpaRepository<UserPolicyYear, UUID> {

    @EntityGraph(attributePaths = {"policyYear"})
    List<UserPolicyYear> findAllByUserIdOrderByPolicyYearYearDesc(UUID userId);

    @EntityGraph(attributePaths = {"policyYear"})
    Optional<UserPolicyYear> findByUserIdAndPolicyYearYear(UUID userId, Integer year);

    @EntityGraph(attributePaths = {"policyYear"})
    Optional<UserPolicyYear> findByIdAndUserId(UUID id, UUID userId);
}
