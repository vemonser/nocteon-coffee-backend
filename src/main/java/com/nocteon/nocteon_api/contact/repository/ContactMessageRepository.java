package com.nocteon.nocteon_api.contact.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.contact.entity.ContactMessage;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    Page<ContactMessage> findByIsReadFalse(Pageable pageable);
}
