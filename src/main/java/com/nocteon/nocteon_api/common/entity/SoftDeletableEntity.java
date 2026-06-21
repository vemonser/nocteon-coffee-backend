package com.nocteon.nocteon_api.common.entity;

import java.time.Instant;

import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@MappedSuperclass
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SQLRestriction("deleted_at IS NULL")
public abstract class SoftDeletableEntity extends BaseEntity {
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
