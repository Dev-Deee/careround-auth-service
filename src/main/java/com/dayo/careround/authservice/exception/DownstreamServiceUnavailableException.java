package com.dayo.careround.authservice.exception;

// A service this request depends on (user-service, ward-service) could not be reached. Maps to 503.
public class DownstreamServiceUnavailableException extends RuntimeException {
    public DownstreamServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
