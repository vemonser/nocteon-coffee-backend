package com.nocteon.nocteon_api.shippingZone.repository;

import java.util.Optional;

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
}