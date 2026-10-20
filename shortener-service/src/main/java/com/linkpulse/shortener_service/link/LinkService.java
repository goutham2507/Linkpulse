// LinkService.java
package com.linkpulse.shortener_service.link;

import com.linkpulse.shortener_service.util.Base62;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

@Service
public class LinkService {

    private static final long TEMP_USER_ID = 1L;   // replaced by the logged-in user in Phase 3

    private final LinkRepository linkRepository;
    private final String baseUrl;

    public LinkService(LinkRepository linkRepository,
                       @Value("${app.base-url}") String baseUrl) {
        this.linkRepository = linkRepository;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public CreateLinkResponse create(CreateLinkRequest request) {
        validate(request.longUrl());

        String tempCode = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Link link = linkRepository.save(new Link(tempCode, request.longUrl(), TEMP_USER_ID));

        link.assignCode(Base62.encode(link.getId()));   // saved automatically at commit

        return new CreateLinkResponse(link.getCode(), baseUrl + "/" + link.getCode(), link.getLongUrl());
    }

    private void validate(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            boolean ok = uri.getHost() != null && scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
            if (!ok) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "URL must be a valid http or https address");
            }
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "URL must be a valid http or https address");
        }
    }
}