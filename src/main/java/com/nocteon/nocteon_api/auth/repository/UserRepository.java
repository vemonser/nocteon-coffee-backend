package com.nocteon.nocteon_api.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Provider;
import com.nocteon.nocteon_api.auth.enums.Role;

import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = """
            SELECT u.*
            FROM users u
            JOIN user_profiles p ON p.user_id = u.id
            WHERE u.email ILIKE CONCAT('%', :query, '%')
               OR p.first_name ILIKE CONCAT('%', :query, '%')
               OR p.last_name ILIKE CONCAT('%', :query, '%')
               OR CONCAT(
                    COALESCE(p.first_name, ''),
                    ' ',
                    COALESCE(p.last_name, '')
                  ) ILIKE CONCAT('%', :query, '%')
            """, nativeQuery = true)
    List<User> searchUsers(@Param("query") String query, Pageable pageable);

    List<User> findByRoleAndSubscribedTrue(Role role);

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

    long countByRoleAndCreatedAtBetween(Role role, Instant start, Instant end);

    long countByRole(Role role);

    @Modifying
    @Query("UPDATE User u SET u.lastActiveAt = :now WHERE u.id = :userId")
    void updateLastActiveAt(@Param("userId") Long userId, @Param("now") Instant now);

    long countByLastActiveAtAfter(Instant threshold);

}
