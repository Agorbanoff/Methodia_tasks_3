package com.methodia.minibilling.export;

import com.methodia.minibilling.model.Invoice;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Service
public class InvoiceFileNameService {

    private static final String[] BULGARIAN_MONTHS = {
            "януари", "февруари", "март", "април", "май", "юни",
            "юли", "август", "септември", "октомври", "ноември", "декември"
    };

    public Path invoicePath(Path outputDirectory, Invoice invoice, YearMonth invoiceMonth) {
        return outputDirectory
                .resolve(directoryName(invoice.consumer(), invoice.reference()))
                .resolve(fileName(invoice.documentNumber(), invoiceMonth))
                .normalize();
    }

    public String directoryName(String consumerName, String reference) {
        return "%s-%s".formatted(sanitizePathName(consumerName), sanitizePathName(reference));
    }

    public String fileName(String documentNumber, YearMonth invoiceMonth) {
        return "%s-%s-%s.json".formatted(
                sanitizePathName(documentNumber),
                bulgarianMonth(invoiceMonth),
                invoiceMonth.format(DateTimeFormatter.ofPattern("yy"))
        );
    }

    public String bulgarianMonth(YearMonth invoiceMonth) {
        return BULGARIAN_MONTHS[invoiceMonth.getMonthValue() - 1];
    }

    private String sanitizePathName(String value) {
        return value.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_");
    }
}

