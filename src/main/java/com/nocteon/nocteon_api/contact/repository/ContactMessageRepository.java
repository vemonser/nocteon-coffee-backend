package com.nocteon.nocteon_api.contact.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.contact.entity.ContactMessage;
import com.nocteon.nocteon_api.contact.entity.ContactStatus;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    Page<ContactMessage> findByIsReadFalse(Pageable pageable);

    Long countByIsReadFalse();

    Page<ContactMessage> findByStatus(ContactStatus status, Pageable pageable);

    Long countByStatus(ContactStatus status);
}
