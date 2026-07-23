package com.methodia.minibilling.repository;

import com.methodia.minibilling.persistence.entity.FileImportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileImportRepository extends JpaRepository<FileImportEntity, String> {
}
