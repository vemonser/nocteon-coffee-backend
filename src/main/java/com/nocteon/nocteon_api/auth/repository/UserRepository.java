package com.nocteon.nocteon_api.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Provider;

import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.profile WHERE u.id = :id")
    Optional<User> findByIdWithProfile(@Param("id") Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.email = :identifier OR u.username = :identifier")
    Optional<User> findByEmailOrUsername(@Param("identifier") String identifier);

    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findByEnabledFalseAndCreatedAtBefore(Instant cutoff);

}
