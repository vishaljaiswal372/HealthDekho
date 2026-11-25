package com.healthDekho.backend.repository;

import com.healthDekho.backend.entity.CustomerPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CustomerPolicyRepository extends JpaRepository<CustomerPolicy, Long> {

    /** FR-1 dashboard: the logged-in customer's policies, product fetched in the same query. */
    @Query("""
            select cp from CustomerPolicy cp
            join fetch cp.product
            where cp.customer.customerId = :customerId
            order by cp.startDate desc, cp.policyId desc
            """)
    List<CustomerPolicy> findByCustomerIdWithProduct(@Param("customerId") Long customerId);

    boolean existsByCustomerCustomerIdAndProductProductId(Long customerId, Long productId);

    @Query("""
            select cp from CustomerPolicy cp
            join fetch cp.customer
            join fetch cp.product
            where cp.policyNumber = :policyNumber
            """)
    Optional<CustomerPolicy> findByPolicyNumberWithDetails(@Param("policyNumber") String policyNumber);

    /** FR-4: lock the policy row while installments and payments are being updated. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cp from CustomerPolicy cp where cp.policyNumber = :policyNumber")
    Optional<CustomerPolicy> findByPolicyNumberForUpdate(@Param("policyNumber") String policyNumber);

    /**
     * FR-5: case-insensitive LIKE on customer name. {@code pattern} must already be lower-case,
     * with LIKE wildcards in the user's input escaped using backslash.
     */
    @Query(value = """
            select cp from CustomerPolicy cp
            join fetch cp.customer c
            join fetch cp.product
            where lower(c.fullName) like :pattern escape '\\'
            order by c.fullName, cp.policyNumber
            """,
            countQuery = """
            select count(cp) from CustomerPolicy cp
            where lower(cp.customer.fullName) like :pattern escape '\\'
            """)
    Page<CustomerPolicy> searchByCustomerName(@Param("pattern") String pattern, Pageable pageable);

    /** FR-5: search by policy identifier + customer date of birth. */
    @Query("""
            select cp from CustomerPolicy cp
            join fetch cp.customer c
            join fetch cp.product
            where cp.policyNumber = :policyNumber and c.dateOfBirth = :dateOfBirth
            """)
    Optional<CustomerPolicy> findByPolicyNumberAndCustomerDob(@Param("policyNumber") String policyNumber,
                                                             @Param("dateOfBirth") LocalDate dateOfBirth);

    /** Highest policy number issued with the given prefix (e.g. "HDP-2026-"), for sequential numbering. */
    @Query("select max(cp.policyNumber) from CustomerPolicy cp where cp.policyNumber like concat(:prefix, '%')")
    Optional<String> findMaxPolicyNumberWithPrefix(@Param("prefix") String prefix);
}
