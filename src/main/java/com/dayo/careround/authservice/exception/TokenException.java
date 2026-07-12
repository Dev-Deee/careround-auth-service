package com.dayo.careround.authservice.exception;

public class TokenException extends RuntimeException {
    public TokenException(String message) {
        super(message);
    }
}