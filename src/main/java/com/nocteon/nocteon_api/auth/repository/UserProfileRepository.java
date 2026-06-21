package com.nocteon.nocteon_api.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.auth.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

}
