package com.methodia.minibilling.service;

import com.methodia.minibilling.repository.BillingErrorLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingErrorLogServiceTest {

    private final BillingErrorLogRepository billingErrorLogRepository =
            org.mockito.Mockito.mock(BillingErrorLogRepository.class);

    private final BillingErrorLogService billingErrorLogService =
            new BillingErrorLogService(billingErrorLogRepository, Clock.systemUTC());

    @Test
    void occurredAtSortUsesCreatedAtEntityProperty() {
        when(billingErrorLogRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        billingErrorLogService.list(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "occurredAt")));

        Pageable pageable = capturedPageable();
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().getOrderFor("occurredAt")).isNull();
    }

    @Test
    void defaultSortUsesCreatedAtDescending() {
        when(billingErrorLogRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        billingErrorLogService.list(PageRequest.of(0, 20));

        Sort.Order order = capturedPageable().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void unsupportedSortPropertyReturnsBadRequestThroughGlobalHandler() {
        assertThatThrownBy(() -> billingErrorLogService.list(
                PageRequest.of(0, 20, Sort.by("unknownFrontendField"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported error log sort property");
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(billingErrorLogRepository).findAll(captor.capture());
        return captor.getValue();
    }
}
