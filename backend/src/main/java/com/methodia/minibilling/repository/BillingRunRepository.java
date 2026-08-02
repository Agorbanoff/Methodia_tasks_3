package com.methodia.minibilling.repository;

import com.methodia.minibilling.persistence.entity.BillingRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillingRunRepository extends JpaRepository<BillingRunEntity, String> {

    Page<BillingRunEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
