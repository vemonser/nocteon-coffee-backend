package com.nocteon.nocteon_api.address.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.address.entity.Address;



public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserId(Long userId);
    Optional<Address> findByUserIdAndIsDefault(Long userId, boolean isDefault);
    Optional<Address> findByIdAndUserId(Long id, Long userId);
}