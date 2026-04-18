package com.clairtax.backend.reliefclaim.repository;

import com.clairtax.backend.reliefclaim.entity.UserReliefClaim;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserReliefClaimRepository extends JpaRepository<UserReliefClaim, UUID> {

    @EntityGraph(attributePaths = {"reliefCategory"})
    List<UserReliefClaim> findAllByUserPolicyYearId(UUID userPolicyYearId);

    Optional<UserReliefClaim> findByUserPolicyYearIdAndReliefCategoryId(
            UUID userPolicyYearId,
            UUID reliefCategoryId
    );
}
