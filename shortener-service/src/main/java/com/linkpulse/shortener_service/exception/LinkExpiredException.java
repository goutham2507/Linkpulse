package com.linkpulse.shortener_service.exception;

public class LinkExpiredException extends RuntimeException {

    public LinkExpiredException(String code) {
        super("Link '" + code + "' has expired");
    }
}