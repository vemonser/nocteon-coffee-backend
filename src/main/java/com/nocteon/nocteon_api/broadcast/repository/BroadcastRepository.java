package com.nocteon.nocteon_api.broadcast.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.nocteon.nocteon_api.broadcast.enums.BroadcastStatus;

import com.nocteon.nocteon_api.broadcast.entity.Broadcast;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

    /** The broadcasts that have not been sent yet. */
    List<Broadcast> findByStatus(BroadcastStatus status);
}
