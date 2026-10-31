package com.dayo.careround.authservice.exception;

public class LicenseNumberRequiredException extends RuntimeException {
    public LicenseNumberRequiredException(String message) {
        super(message);
    }
}
