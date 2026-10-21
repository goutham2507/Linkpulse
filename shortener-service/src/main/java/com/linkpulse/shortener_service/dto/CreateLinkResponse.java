// CreateLinkResponse.java
package com.linkpulse.shortener_service.dto;

public record CreateLinkResponse(String code, String shortUrl, String longUrl) { }