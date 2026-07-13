package com.nocteon.nocteon_api.broadcast.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.enums.Role;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.broadcast.dto.request.CreateBroadcastRequest;
import com.nocteon.nocteon_api.broadcast.dto.response.BroadcastResponse;
import com.nocteon.nocteon_api.broadcast.entity.Broadcast;
import com.nocteon.nocteon_api.broadcast.entity.BroadcastRecipient;
import com.nocteon.nocteon_api.broadcast.enums.BroadcastRecipientStatus;
import com.nocteon.nocteon_api.broadcast.enums.BroadcastStatus;
import com.nocteon.nocteon_api.broadcast.exception.BroadcastNotFoundException;
import com.nocteon.nocteon_api.broadcast.repository.BroadcastRecipientRepository;
import com.nocteon.nocteon_api.broadcast.repository.BroadcastRepository;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BroadcastCreationService {

    private final BroadcastRepository broadcastRepository;
    private final BroadcastRecipientRepository recipientRepository;
    private final UserRepository userRepository;

    @Transactional
    public BroadcastResponse createBroadcast(CreateBroadcastRequest request) {
List<User> eligibleUsers = userRepository.findByRoleAndSubscribedTrue(Role.CUSTOMER);

        Broadcast broadcast = Broadcast.builder()
                .subject(request.getSubject())
                .content(request.getContent())
                .status(BroadcastStatus.PENDING)
                .totalRecipients(eligibleUsers.size())
                .sentCount(0)
                .failedCount(0)
                .build();

        broadcastRepository.saveAndFlush(broadcast);

        List<BroadcastRecipient> recipients = eligibleUsers.stream()
                .map(user -> buildRecipient(broadcast, user))
                .toList();

        recipientRepository.saveAll(recipients);

        return buildResponse(broadcast);
    }

    private BroadcastRecipient buildRecipient(Broadcast broadcast, User user) {
        return BroadcastRecipient.builder()
                .broadcast(broadcast)
                .user(user)
                .status(BroadcastRecipientStatus.PENDING)
                .attemptCount(0)
                .build();
    }

    public PageResponse<BroadcastResponse> getAll(BaseFilterRequest filter) {
        Page<Broadcast> page = broadcastRepository.findAll(filter.toPageable());
        return PageResponse.of(page.map(this::buildResponse));
    }

    public BroadcastResponse getById(Long id) {
        Broadcast broadcast = broadcastRepository.findById(id)
                .orElseThrow(BroadcastNotFoundException::new);
        return buildResponse(broadcast);
    }

    private BroadcastResponse buildResponse(Broadcast broadcast) {
        return BroadcastResponse.builder()
                .id(broadcast.getId())
                .subject(broadcast.getSubject())
                .content(broadcast.getContent())
                .status(broadcast.getStatus())
                .totalRecipients(broadcast.getTotalRecipients())
                .sentCount(broadcast.getSentCount())
                .failedCount(broadcast.getFailedCount())
                .createdAt(broadcast.getCreatedAt())
                .build();
    }
}