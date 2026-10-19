package com.nocteon.nocteon_api.shippingZone.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.shippingZone.entity.ShippingZone;

public interface ShippingZoneRepository extends JpaRepository<ShippingZone, Long> {

    @Query("""
        SELECT z FROM ShippingZone z
        JOIN z.cities c
        WHERE c = :city AND z.active = true
    """)
    Optional<ShippingZone> findActiveZoneByCity(@Param("city") String city);

    boolean existsByCitiesContainingAndIdNot(String city, Long excludeId);

    boolean existsByCitiesContaining(String city);

    @Query(value = "SELECT e.* FROM shipping_zones e WHERE e.deleted_at IS NOT NULL", nativeQuery = true)
    List<ShippingZone> findAllDeletedNative();

    @Query(value = "SELECT e.* FROM shipping_zones e WHERE e.deleted_at IS NOT NULL AND (LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%')))", nativeQuery = true)
    List<ShippingZone> findDeletedBySearchNative(@Param("search") String search);

    @Query(value = "SELECT e.* FROM shipping_zones e WHERE e.id = :id AND e.deleted_at IS NOT NULL", nativeQuery = true)
    Optional<ShippingZone> findDeletedById(@Param("id") Long id);
}