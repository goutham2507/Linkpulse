package com.linkpulse.shortener_service.exception;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String code) {
        super("Short code '" + code + "' not found");
    }
}