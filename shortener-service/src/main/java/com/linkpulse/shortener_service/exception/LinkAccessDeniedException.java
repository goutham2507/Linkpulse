package com.linkpulse.shortener_service.exception;

public class LinkAccessDeniedException extends RuntimeException {

    public LinkAccessDeniedException(String code) {
        super("You do not have permission to modify link '" + code + "'");
    }
}