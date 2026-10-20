// CreateLinkResponse.java
package com.linkpulse.shortener_service.link;

public record CreateLinkResponse(String code, String shortUrl, String longUrl) { }