package com.linkpulse.shortener_service.exception;

public class LinkDisabledException extends RuntimeException {

    public LinkDisabledException(String code) {
        super("Link '" + code + "' has been disabled");
    }
}
