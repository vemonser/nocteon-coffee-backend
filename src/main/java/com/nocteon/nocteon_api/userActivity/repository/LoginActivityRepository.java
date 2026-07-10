package com.nocteon.nocteon_api.userActivity.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nocteon.nocteon_api.userActivity.entity.LoginActivity;

import io.lettuce.core.dynamic.annotation.Param;

public interface LoginActivityRepository extends JpaRepository<LoginActivity, Long> {

    @Query("""
        SELECT l.deviceType, COUNT(l)
        FROM LoginActivity l
        WHERE l.createdAt >= :since
        GROUP BY l.deviceType
    """)
    List<Object[]> countByDeviceTypeSince(@Param("since") Instant since);
}