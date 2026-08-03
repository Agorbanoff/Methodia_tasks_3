package com.methodia.minibilling.service.invoice;

import com.methodia.minibilling.controller.dto.invoice.InvoiceDetailResponse;
import com.methodia.minibilling.exception.InvoiceNotFoundException;
import com.methodia.minibilling.mapper.InvoiceEntityMapper;
import com.methodia.minibilling.mapper.InvoiceMapper;
import com.methodia.minibilling.model.invoice.Invoice;
import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.InvoiceEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import com.methodia.minibilling.repository.CustomerRepository;
import com.methodia.minibilling.repository.InvoiceRepository;
import com.methodia.minibilling.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceQueryService {

    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceEntityMapper invoiceEntityMapper;

    public InvoiceQueryService(
            InvoiceRepository invoiceRepository,
            UserRepository userRepository,
            CustomerRepository customerRepository,
            InvoiceEntityMapper invoiceEntityMapper
    ) {
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.invoiceEntityMapper = invoiceEntityMapper;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceDetailResponse> findVisibleInvoices(Optional<String> requestedReference, Pageable pageable,
                                                           String username) {
        UserEntity account = findAccount(username);
        Page<InvoiceEntity> invoices;
        if (isAdmin(account)) {
            invoices = findForAdmin(requestedReference, pageable);
        } else {
            invoices = invoiceRepository.findByCustomer(linkedCustomer(account), pageable);
        }
        return invoices.map(invoiceEntityMapper::toModel).map(InvoiceMapper::toDetail);
    }

    @Transactional(readOnly = true)
    public List<Invoice> findAll(Optional<Integer> year, Optional<Integer> month) {
        Optional<YearMonth> invoiceMonth = toYearMonth(year, month);
        List<InvoiceEntity> invoices = invoiceMonth
                .map(monthValue -> invoiceRepository.findByBillingYearAndBillingMonthOrderByNumberAsc(
                        monthValue.getYear(),
                        monthValue.getMonthValue()
                ))
                .orElseGet(invoiceRepository::findAllByOrderByNumberAsc);
        return invoices.stream()
                .map(invoiceEntityMapper::toModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public Invoice findByDocumentNumber(String documentNumber) {
        return invoiceRepository.findByNumber(documentNumber)
                .map(invoiceEntityMapper::toModel)
                .orElseThrow(() -> new InvoiceNotFoundException(documentNumber));
    }

    @Transactional(readOnly = true)
    public InvoiceDetailResponse findVisibleByDocumentNumber(String documentNumber, String username) {
        UserEntity account = findAccount(username);
        InvoiceEntity invoice = invoiceRepository.findByNumber(documentNumber)
                .orElseThrow(() -> new InvoiceNotFoundException(documentNumber));
        if (!isAdmin(account) && !invoice.getCustomer().equals(linkedCustomer(account))) {
            throw new InvoiceNotFoundException(documentNumber);
        }
        return InvoiceMapper.toDetail(invoiceEntityMapper.toModel(invoice));
    }

    private Page<InvoiceEntity> findForAdmin(Optional<String> requestedReference, Pageable pageable) {
        if (requestedReference.isEmpty()) {
            return invoiceRepository.findAllByOrderByNumberAsc(pageable);
        }
        return customerRepository.findByReference(requestedReference.get())
                .map(customer -> invoiceRepository.findByCustomer(customer, pageable))
                .orElseGet(() -> Page.empty(pageable));
    }

    private UserEntity findAccount(String username) {
        UserEntity account = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated account not found"));
        return account;
    }

    private boolean isAdmin(UserEntity account) {
        return "ADMIN".equals(account.getRole());
    }

    private CustomerEntity linkedCustomer(UserEntity account) {
        if (account.getCustomer() == null) {
            throw new IllegalArgumentException("Authenticated USER account is not linked to a customer");
        }
        return account.getCustomer();
    }

    private Optional<YearMonth> toYearMonth(Optional<Integer> year, Optional<Integer> month) {
        if (year.isEmpty() && month.isEmpty()) {
            return Optional.empty();
        }
        if (year.isEmpty() || month.isEmpty()) {
            throw new IllegalArgumentException("Both year and month must be provided when filtering invoices");
        }
        return Optional.of(YearMonth.of(year.get(), month.get()));
    }
}
