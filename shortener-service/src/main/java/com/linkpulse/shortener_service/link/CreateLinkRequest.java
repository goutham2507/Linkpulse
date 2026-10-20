// CreateLinkRequest.java
package com.linkpulse.shortener_service.link;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLinkRequest(@NotBlank @Size(max = 2048) String longUrl) { }