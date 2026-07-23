package com.methodia.minibilling.repository;

import com.methodia.minibilling.persistence.entity.InvoiceLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLineEntity, String> {
}
