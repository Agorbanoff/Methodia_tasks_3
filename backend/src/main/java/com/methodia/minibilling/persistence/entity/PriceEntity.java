package com.methodia.minibilling.persistence.entity;

import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.persistence.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "prices")
public class PriceEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "product", length = 20, nullable = false)
    private Product product;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "price_list", nullable = false)
    private int priceList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_import_id")
    private FileImportEntity fileImport;

    public PriceEntity(String id, Product product, LocalDate startDate, LocalDate endDate, BigDecimal price,
                       int priceList, FileImportEntity fileImport) {
        this.id = id;
        this.product = product;
        this.startDate = startDate;
        this.endDate = endDate;
        this.price = price;
        this.priceList = priceList;
        this.fileImport = fileImport;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = IdGenerator.generateId();
        }
    }
}
