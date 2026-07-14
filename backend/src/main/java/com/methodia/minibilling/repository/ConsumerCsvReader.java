package com.methodia.minibilling.repository;

import com.methodia.minibilling.model.Consumer;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class ConsumerCsvReader {

    public List<Consumer> read(Path file) {
        return CsvRowMapper.readRows(file, 3, row -> new Consumer(
                row.requiredString(0, "name"),
                row.requiredString(1, "reference"),
                row.requiredInt(2, "priceListNumber")
        ));
    }
}

