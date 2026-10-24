package com.nocteon.nocteon_api.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.auth.entity.RoleEntity;

public interface RoleRepository extends JpaRepository<RoleEntity, String> {

    @EntityGraph(attributePaths = "permissions")
    @Query("SELECT r FROM RoleEntity r")
    java.util.List<RoleEntity> findAllWithPermissions();

    @EntityGraph(attributePaths = "permissions")
    Optional<RoleEntity> findWithPermissionsByName(String name);
}
