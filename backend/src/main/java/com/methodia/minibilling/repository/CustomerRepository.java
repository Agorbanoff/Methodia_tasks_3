package com.methodia.minibilling.repository;

import com.methodia.minibilling.persistence.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, String> {

    Optional<CustomerEntity> findByReference(String reference);

    boolean existsByReference(String reference);
}
