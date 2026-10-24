package com.linkpulse.shortener_service.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("Email '" + email + "' is already registered");
    }
}
