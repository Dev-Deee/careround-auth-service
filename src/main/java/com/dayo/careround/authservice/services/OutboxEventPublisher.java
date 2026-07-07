package com.dayo.careround.authservice.services;

import com.dayo.careround.authservice.data.models.OutboxEvent;
import com.dayo.careround.authservice.data.models.enums.OutboxStatus;
import com.dayo.careround.authservice.data.repositories.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private int consecutiveFailures = 0;
    private boolean circuitOpen = false;
    private static final int CIRCUIT_OPEN_THRESHOLD = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 100;

    @Scheduled(fixedDelay = 5000)
    public void processPendingEvents() {
        if (circuitOpen) {
            log.warn("Circuit open — skipping outbox poll");
            return;
        }

        Page<OutboxEvent> pending = outboxEventRepository
                .findByStatusAndNextRetryAtLessThanEqual(
                        OutboxStatus.PENDING,
                        Instant.now(),
                        PageRequest.of(0, BATCH_SIZE)
                );

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getEventType(), event.getIdempotencyKey(), event.getPayload());
                event.setStatus(OutboxStatus.SENT);
                consecutiveFailures = 0;
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
                event.setAttempts(event.getAttempts() + 1);
                event.setLastAttemptAt(Instant.now());

                if (event.getAttempts() >= MAX_ATTEMPTS) {
                    event.setStatus(OutboxStatus.DEAD_LETTER);
                    log.error("Outbox event {} moved to DEAD_LETTER after {} attempts", event.getId(), MAX_ATTEMPTS);
                } else {
                    // exponential backoff
                    long backoffSeconds = (long) Math.pow(2, event.getAttempts()) * 5;
                    event.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
                }

                consecutiveFailures++;
                if (consecutiveFailures >= CIRCUIT_OPEN_THRESHOLD) {
                    circuitOpen = true;
                    log.error("Circuit opened — Kafka appears to be down");
                }
            }
            outboxEventRepository.save(event);
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void checkCircuit() {
        if (circuitOpen) {
            log.info("Checking if Kafka has recovered...");
            try {
                kafkaTemplate.send("health-check", "ping").get();
                circuitOpen = false;
                consecutiveFailures = 0;
                log.info("Kafka recovered — circuit closed");
            } catch (Exception e) {
                log.warn("Kafka still down — circuit remains open");
            }
        }
    }
}