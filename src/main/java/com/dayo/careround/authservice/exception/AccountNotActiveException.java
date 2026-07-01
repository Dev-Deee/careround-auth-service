package com.dayo.careround.authservice.exception;

public class AccountNotActiveException extends AuthException {
    public AccountNotActiveException() {
        super("Account is not active");
    }
}