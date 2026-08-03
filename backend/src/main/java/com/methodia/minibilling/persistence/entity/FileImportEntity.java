package com.methodia.minibilling.persistence.entity;

import com.methodia.minibilling.model.importing.ImportType;
import com.methodia.minibilling.persistence.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "file_imports")
public class FileImportEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private ImportType type;

    @Column(name = "filename", nullable = false)
    private String filename;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private UserEntity uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "file_content", columnDefinition = "bytea")
    private byte[] fileContent;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "imported_records")
    private Integer importedRecords;

    @Column(name = "error_count")
    private Integer errorCount;

    public FileImportEntity(String id, ImportType type, String filename, UserEntity uploadedBy,
                            OffsetDateTime uploadedAt, byte[] fileContent) {
        this.id = id;
        this.type = type;
        this.filename = filename;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.fileContent = fileContent;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = IdGenerator.generateId();
        }
    }
}
