package com.methodia.minibilling.repository;

import com.methodia.minibilling.persistence.entity.InvoiceEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<InvoiceEntity, String> {

    Optional<InvoiceEntity> findByNumber(String number);

    Optional<InvoiceEntity> findByUserAndBillingYearAndBillingMonth(UserEntity user, int billingYear, int billingMonth);

    boolean existsByUserAndBillingYearAndBillingMonth(UserEntity user, int billingYear, int billingMonth);

    List<InvoiceEntity> findAllByOrderByNumberAsc();

    List<InvoiceEntity> findByBillingYearAndBillingMonthOrderByNumberAsc(int billingYear, int billingMonth);
}
