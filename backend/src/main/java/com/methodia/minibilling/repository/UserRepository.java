package com.methodia.minibilling.repository;

import com.methodia.minibilling.persistence.entity.CustomerEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByReference(String reference);

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByCustomer(CustomerEntity customer);

    boolean existsByReference(String reference);

    boolean existsByUsername(String username);

    boolean existsByCustomer(CustomerEntity customer);
}
