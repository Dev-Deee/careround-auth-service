package com.dayo.careround.authservice.exception;

public class AccountNotCancellableException extends RuntimeException {
    public AccountNotCancellableException(String message) {
        super(message);
    }
}
