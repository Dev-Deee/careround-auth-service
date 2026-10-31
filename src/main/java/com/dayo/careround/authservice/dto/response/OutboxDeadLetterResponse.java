package com.dayo.careround.authservice.dto.response;

import com.dayo.careround.authservice.data.models.OutboxEvent;
import com.dayo.careround.authservice.data.models.enums.OutboxStatus;

import java.time.Instant;
import java.util.UUID;

// Operational view of a dead-lettered outbox event. The payload (names, emails, licence numbers) is never
// returned; eventType is the Kafka topic and idempotencyKey the record key (event prefix + user id).
public record OutboxDeadLetterResponse(
        UUID id,
        String eventType,
        String idempotencyKey,
        OutboxStatus status,
        int attempts,
        Instant createdAt,
        Instant lastAttemptAt,
        Instant nextRetryAt
) {
    public static OutboxDeadLetterResponse from(OutboxEvent event) {
        return new OutboxDeadLetterResponse(event.getId(), event.getEventType(), event.getIdempotencyKey(),
                event.getStatus(), event.getAttempts(), event.getCreatedAt(), event.getLastAttemptAt(), event.getNextRetryAt());
    }
}
