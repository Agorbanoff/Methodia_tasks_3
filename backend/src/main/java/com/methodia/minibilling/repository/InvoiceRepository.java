package com.methodia.minibilling.repository;

import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.InvoiceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<InvoiceEntity, String> {

    Optional<InvoiceEntity> findByNumber(String number);

    Optional<InvoiceEntity> findByCustomerAndBillingYearAndBillingMonth(CustomerEntity customer, int billingYear, int billingMonth);

    boolean existsByCustomerAndBillingYearAndBillingMonth(CustomerEntity customer, int billingYear, int billingMonth);

    List<InvoiceEntity> findAllByOrderByNumberAsc();

    List<InvoiceEntity> findByBillingYearAndBillingMonthOrderByNumberAsc(int billingYear, int billingMonth);

    Page<InvoiceEntity> findAllByOrderByNumberAsc(Pageable pageable);

    Page<InvoiceEntity> findByCustomer(CustomerEntity customer, Pageable pageable);
}
