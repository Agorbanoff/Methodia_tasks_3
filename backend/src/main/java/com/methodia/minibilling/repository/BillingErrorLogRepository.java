package com.methodia.minibilling.repository;

import com.methodia.minibilling.persistence.entity.BillingErrorLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface BillingErrorLogRepository extends JpaRepository<BillingErrorLogEntity, String> {

    void deleteByCreatedAtBefore(OffsetDateTime threshold);
}
