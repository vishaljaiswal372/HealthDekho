package com.healthDekho.backend.repository;

import com.healthDekho.backend.entity.InsuranceProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InsuranceProductRepository extends JpaRepository<InsuranceProduct, Long> {

    List<InsuranceProduct> findByActiveTrueOrderByPolicyNameAsc();

    Optional<InsuranceProduct> findByProductIdAndActiveTrue(Long productId);

    Optional<InsuranceProduct> findByProductCode(String productCode);

    /** FR-1 / FR-2: active products the customer has not registered yet (duplicate-registration rule). */
    @Query("""
            select p from InsuranceProduct p
            where p.active = true
              and not exists (
                  select 1 from CustomerPolicy cp
                  where cp.product = p and cp.customer.customerId = :customerId)
            order by p.policyName
            """)
    List<InsuranceProduct> findAvailableForCustomer(@Param("customerId") Long customerId);
}
