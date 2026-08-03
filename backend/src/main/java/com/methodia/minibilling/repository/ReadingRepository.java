package com.methodia.minibilling.repository;

import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.ReadingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReadingRepository extends JpaRepository<ReadingEntity, String> {

    List<ReadingEntity> findByCustomerOrderByDateTimeAsc(CustomerEntity customer);

    List<ReadingEntity> findAllByOrderByDateTimeDesc();

    List<ReadingEntity> findByCustomerOrderByDateTimeDesc(CustomerEntity customer);

    List<ReadingEntity> findByCustomerAndProductOrderByDateTimeAsc(CustomerEntity customer, Product product);

    boolean existsByCustomerAndProductAndDateTime(CustomerEntity customer, Product product, OffsetDateTime dateTime);

    boolean existsByCustomerAndProductAndDateTimeGreaterThanEqualAndDateTimeLessThan(
            CustomerEntity customer,
            Product product,
            OffsetDateTime start,
            OffsetDateTime end
    );

    Optional<ReadingEntity> findFirstByCustomerAndProductAndDateTimeBeforeOrderByDateTimeDesc(
            CustomerEntity customer,
            Product product,
            OffsetDateTime dateTime
    );
}
