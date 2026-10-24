package com.nocteon.nocteon_api.auth.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleEntity {

    @Id
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean system = false;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_name"))
    @Column(name = "permission", nullable = false, length = 64)
    @Builder.Default
    private Set<String> permissions = new HashSet<>();
}
