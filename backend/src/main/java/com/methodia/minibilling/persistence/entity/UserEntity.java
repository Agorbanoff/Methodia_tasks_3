package com.methodia.minibilling.persistence.entity;

import com.methodia.minibilling.persistence.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "reference", length = 100, nullable = false, unique = true)
    private String reference;

    @Column(name = "price_list", nullable = false)
    private int priceList;

    @OneToMany(mappedBy = "user")
    private List<ReadingEntity> readings = new ArrayList<>();

    public UserEntity(String name, String reference, int priceList) {
        this.name = name;
        this.reference = reference;
        this.priceList = priceList;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = IdGenerator.generateId();
        }
    }
}
