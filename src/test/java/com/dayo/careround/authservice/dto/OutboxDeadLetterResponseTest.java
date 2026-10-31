package com.dayo.careround.authservice.dto;

import com.dayo.careround.authservice.data.models.OutboxEvent;
import com.dayo.careround.authservice.data.models.enums.OutboxStatus;
import com.dayo.careround.authservice.dto.response.OutboxDeadLetterResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxDeadLetterResponseTest {

    @Test
    void deadLetterViewCarriesNoPayloadOrPersonalData() throws Exception {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setEventType("user.registered");
        event.setIdempotencyKey("user-registered-" + UUID.randomUUID());
        event.setStatus(OutboxStatus.DEAD_LETTER);
        event.setAttempts(5);
        event.setPayload("{\"email\":\"jane.doe@hospital.example\",\"firstName\":\"Jane\",\"licenseNumber\":\"LIC-123\"}");

        String json = new ObjectMapper().registerModule(new JavaTimeModule())
                .writeValueAsString(OutboxDeadLetterResponse.from(event));

        assertThat(json).doesNotContain("payload").doesNotContain("jane.doe").doesNotContain("Jane").doesNotContain("LIC-123");
        assertThat(json).contains("\"eventType\":\"user.registered\"").contains("\"attempts\":5").contains("\"status\":\"DEAD_LETTER\"");
    }
}
