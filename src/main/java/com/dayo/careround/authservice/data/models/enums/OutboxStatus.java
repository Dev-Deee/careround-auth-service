package com.dayo.careround.authservice.data.models.enums;

public enum OutboxStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED,
    DEAD_LETTER
}