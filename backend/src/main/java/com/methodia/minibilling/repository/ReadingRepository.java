package com.methodia.minibilling.repository;

import com.methodia.minibilling.model.Product;
import com.methodia.minibilling.persistence.entity.ReadingEntity;
import com.methodia.minibilling.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ReadingRepository extends JpaRepository<ReadingEntity, String> {

    List<ReadingEntity> findByUserOrderByDateTimeAsc(UserEntity user);

    List<ReadingEntity> findByUserAndProductOrderByDateTimeAsc(UserEntity user, Product product);

    boolean existsByUserAndProductAndDateTime(UserEntity user, Product product, OffsetDateTime dateTime);
}
