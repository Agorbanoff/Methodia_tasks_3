package com.methodia.minibilling.service.billing;

import com.methodia.minibilling.model.tariff.Product;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.PriceEntity;
import com.methodia.minibilling.repository.PriceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TariffSnapshotService {

    private final PriceRepository priceRepository;

    public TariffSnapshotService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    public String createSnapshot(CustomerEntity customer) {
        List<PriceEntity> prices = new ArrayList<>();
        for (Product product : Product.values()) {
            prices.addAll(priceRepository.findByPriceListAndProductOrderByStartDateAsc(customer.getPriceList(), product));
        }
        prices.sort(Comparator.comparing(PriceEntity::getProduct).thenComparing(PriceEntity::getStartDate));

        StringBuilder snapshot = new StringBuilder();
        for (PriceEntity price : prices) {
            snapshot.append(price.getProduct().name())
                    .append('|')
                    .append(price.getStartDate())
                    .append('|')
                    .append(price.getEndDate())
                    .append('|')
                    .append(price.getPrice())
                    .append('|')
                    .append(price.getPriceList())
                    .append('\n');
        }
        return snapshot.toString();
    }

    public List<PriceEntity> readSnapshot(String snapshot) {
        List<PriceEntity> prices = new ArrayList<>();
        if (snapshot == null || snapshot.isBlank()) {
            return prices;
        }

        String[] lines = snapshot.split("\\R");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split("\\|", -1);
            if (fields.length < 5) {
                throw new IllegalArgumentException("Invalid tariff snapshot row");
            }

            PriceEntity price = new PriceEntity(
                    null,
                    Product.valueOf(fields[0]),
                    LocalDate.parse(fields[1]),
                    LocalDate.parse(fields[2]),
                    new BigDecimal(fields[3]),
                    Integer.parseInt(fields[4]),
                    null
            );
            prices.add(price);
        }
        return prices;
    }
}
