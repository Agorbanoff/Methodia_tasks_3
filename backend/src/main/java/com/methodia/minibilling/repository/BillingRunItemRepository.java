package com.methodia.minibilling.repository;

import com.methodia.minibilling.model.billing.BillingRunItemStatus;
import com.methodia.minibilling.model.billing.BillingRunStatus;
import com.methodia.minibilling.persistence.entity.BillingRunEntity;
import com.methodia.minibilling.persistence.entity.BillingRunItemEntity;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingRunItemRepository extends JpaRepository<BillingRunItemEntity, String> {

    List<BillingRunItemEntity> findByBillingRunOrderByCustomerReferenceAsc(BillingRunEntity billingRun);

    List<BillingRunItemEntity> findByBillingRunAndStatusOrderByCustomerReferenceAsc(
            BillingRunEntity billingRun,
            BillingRunItemStatus status
    );

    Optional<BillingRunItemEntity> findFirstByBillingRunIdAndStatusOrderByCustomerReferenceAsc(
            String billingRunId,
            BillingRunItemStatus status
    );

    int countByBillingRunAndStatus(BillingRunEntity billingRun, BillingRunItemStatus status);

    int countByBillingRunAndStatusIn(BillingRunEntity billingRun, Collection<BillingRunItemStatus> statuses);

    @Query("""
            select count(item) > 0
            from BillingRunItemEntity item
            where item.customer = :customer
              and item.billingRun.periodStart = :periodStart
              and item.billingRun.periodEnd = :periodEnd
              and item.billingRun.status in :runStatuses
              and item.status in :itemStatuses
            """)
    boolean existsActiveItemForCustomerAndPeriod(
            CustomerEntity customer,
            LocalDate periodStart,
            LocalDate periodEnd,
            Collection<BillingRunStatus> runStatuses,
            Collection<BillingRunItemStatus> itemStatuses
    );
}
