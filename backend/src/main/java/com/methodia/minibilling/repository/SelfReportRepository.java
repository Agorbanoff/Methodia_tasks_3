package com.methodia.minibilling.repository;

import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.model.reading.SelfReportStatus;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.SelfReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SelfReportRepository extends JpaRepository<SelfReportEntity, String> {

    List<SelfReportEntity> findAllByOrderByRequestedAtDesc();

    List<SelfReportEntity> findByCustomerOrderByRequestedAtDesc(CustomerEntity customer);

    boolean existsByCustomerAndServiceAndReadingDateAndStatus(
            CustomerEntity customer,
            Product service,
            LocalDate readingDate,
            SelfReportStatus status
    );
}
