package com.methodia.minibilling.csv;

import com.methodia.minibilling.model.Reading;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class ReadingCsvReader {

    public List<Reading> read(Path file) {
        return CsvRowMapper.readRows(file, 4, row -> new Reading(
                row.requiredString(0, "reference"),
                row.requiredString(1, "product"),
                row.requiredOffsetDateTime(2, "dateTime"),
                row.requiredBigDecimal(3, "value")
        ));
    }
}

