package com.dayo.careround.authservice.exception;

public class InvalidCredentialsException extends AuthException {
    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}