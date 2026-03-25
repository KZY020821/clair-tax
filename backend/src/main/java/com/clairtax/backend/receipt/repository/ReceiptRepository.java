package com.clairtax.backend.receipt.repository;

import com.clairtax.backend.receipt.entity.Receipt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    interface ReliefCategoryReceiptCount {

        UUID getReliefCategoryId();

        Long getReceiptCount();
    }

    @Query("""
            select distinct receipt.userPolicyYear.policyYear.year
            from Receipt receipt
            where receipt.userPolicyYear.user.id = :userId
            order by receipt.userPolicyYear.policyYear.year desc
            """)
    List<Integer> findDistinctPolicyYearsByUserId(UUID userId);

    @EntityGraph(attributePaths = {"reliefCategory", "userPolicyYear", "userPolicyYear.policyYear"})
    @Query("""
            select receipt
            from Receipt receipt
            where receipt.userPolicyYear.user.id = :userId
            and receipt.userPolicyYear.policyYear.year = :policyYear
            order by receipt.receiptDate desc, receipt.createdAt desc
            """)
    List<Receipt> findAllDetailedByUserIdAndPolicyYear(UUID userId, Integer policyYear);

    @EntityGraph(attributePaths = {"reliefCategory", "userPolicyYear", "userPolicyYear.policyYear"})
    @Query("""
            select receipt
            from Receipt receipt
            where receipt.userPolicyYear.user.id = :userId
            order by receipt.userPolicyYear.policyYear.year desc, receipt.receiptDate desc, receipt.createdAt desc
            """)
    List<Receipt> findAllDetailedByUserId(UUID userId);

    @EntityGraph(attributePaths = {"reliefCategory", "userPolicyYear", "userPolicyYear.policyYear"})
    @Query("""
            select receipt
            from Receipt receipt
            where receipt.userPolicyYear.id = :userPolicyYearId
            order by receipt.receiptDate desc, receipt.createdAt desc
            """)
    List<Receipt> findAllDetailedByUserPolicyYearId(UUID userPolicyYearId);

    @EntityGraph(attributePaths = {"reliefCategory", "userPolicyYear", "userPolicyYear.policyYear"})
    @Query("""
            select receipt
            from Receipt receipt
            where receipt.id = :id
            and receipt.userPolicyYear.user.id = :userId
            """)
    Optional<Receipt> findDetailedByIdAndUserId(UUID id, UUID userId);

    @Query("""
            select coalesce(sum(receipt.amount), 0)
            from Receipt receipt
            where receipt.userPolicyYear.id = :userPolicyYearId
            and receipt.reliefCategory.id = :reliefCategoryId
            """)
    BigDecimal sumAmountByUserPolicyYearIdAndReliefCategoryId(UUID userPolicyYearId, UUID reliefCategoryId);

    @Query("""
            select receipt.reliefCategory.id as reliefCategoryId, count(receipt.id) as receiptCount
            from Receipt receipt
            where receipt.userPolicyYear.id = :userPolicyYearId
            and receipt.reliefCategory is not null
            group by receipt.reliefCategory.id
            """)
    List<ReliefCategoryReceiptCount> countReceiptsByUserPolicyYearIdGroupedByReliefCategoryId(UUID userPolicyYearId);
}
