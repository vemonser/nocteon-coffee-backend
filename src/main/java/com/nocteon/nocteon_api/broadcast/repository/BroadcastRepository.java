package com.nocteon.nocteon_api.broadcast.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.broadcast.entity.Broadcast;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {
}
