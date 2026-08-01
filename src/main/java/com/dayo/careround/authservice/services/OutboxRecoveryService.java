package com.dayo.careround.authservice.services;

import com.dayo.careround.authservice.data.models.OutboxEvent;
import com.dayo.careround.authservice.data.models.enums.OutboxStatus;
import com.dayo.careround.authservice.data.repositories.OutboxEventRepository;
import com.dayo.careround.authservice.exception.UserNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxRecoveryService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void retryDeadLetter(UUID id) {
        OutboxEvent event = outboxEventRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("No outbox event with that id"));

        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);
        event.setNextRetryAt(Instant.now());
        outboxEventRepository.save(event);
    }
}