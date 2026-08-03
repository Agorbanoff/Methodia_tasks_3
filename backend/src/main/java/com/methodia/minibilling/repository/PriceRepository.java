package com.methodia.minibilling.repository;

import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.persistence.entity.PriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PriceRepository extends JpaRepository<PriceEntity, String> {

    List<PriceEntity> findByPriceListAndProductOrderByStartDateAsc(int priceList, Product product);

    List<PriceEntity> findByTariffCodeAndProductOrderByStartDateAsc(String tariffCode, Product product);

    boolean existsByPriceListAndProductAndStartDateAndEndDate(int priceList, Product product, LocalDate startDate, LocalDate endDate);

    boolean existsByTariffCodeAndProductAndStartDateAndEndDate(String tariffCode, Product product, LocalDate startDate, LocalDate endDate);
}
