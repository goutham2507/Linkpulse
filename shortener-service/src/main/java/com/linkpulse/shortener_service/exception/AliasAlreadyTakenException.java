package com.linkpulse.shortener_service.exception;

public class AliasAlreadyTakenException extends RuntimeException {

    public AliasAlreadyTakenException(String alias) {
        super("Alias '" + alias + "' is already taken");
    }
}