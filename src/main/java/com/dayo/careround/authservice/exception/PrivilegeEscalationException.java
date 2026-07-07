package com.dayo.careround.authservice.exception;

public class PrivilegeEscalationException extends RuntimeException {
    public PrivilegeEscalationException(String message) {
        super(message);
    }
}
