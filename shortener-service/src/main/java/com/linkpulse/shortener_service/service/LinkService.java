package com.linkpulse.shortener_service.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkpulse.shortener_service.dto.CreateLinkRequest;
import com.linkpulse.shortener_service.dto.CreateLinkResponse;
import com.linkpulse.shortener_service.entity.Link;
import com.linkpulse.shortener_service.exception.AliasAlreadyTakenException;
import com.linkpulse.shortener_service.exception.InvalidUrlException;
import com.linkpulse.shortener_service.exception.LinkExpiredException;
import com.linkpulse.shortener_service.exception.LinkNotFoundException;
import com.linkpulse.shortener_service.repository.LinkRepository;
import com.linkpulse.shortener_service.util.Base62;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.linkpulse.shortener_service.dto.LinkSummary;
import com.linkpulse.shortener_service.dto.PageResponse;
import com.linkpulse.shortener_service.exception.LinkAccessDeniedException;
@Service
public class LinkService {

   // private static final long TEMP_USER_ID = 1L;   // replaced by logged-in user in Phase 3

    private final LinkRepository linkRepository;
    private final String baseUrl;

    public LinkService(LinkRepository linkRepository,
                       @Value("${app.base-url}") String baseUrl) {
        this.linkRepository = linkRepository;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public CreateLinkResponse create(CreateLinkRequest request, Long userId) {
        validate(request.longUrl());

        String alias = request.alias();
        if (alias != null && linkRepository.existsByCode(alias)) {
            throw new AliasAlreadyTakenException(alias);
        }

        String initialCode = (alias != null)
                ? alias
                : UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        Link link = new Link(initialCode, request.longUrl(), userId);
        link.setExpiresAt(request.expiresAt());
        link = linkRepository.save(link);

        if (alias == null) {
            link.assignCode(Base62.encode(link.getId()));   // saved automatically at commit
        }

        return new CreateLinkResponse(link.getCode(), baseUrl + "/" + link.getCode(), link.getLongUrl());
    }

    @Transactional(readOnly = true)
    public String resolve(String code) {
        Link link = linkRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new LinkExpiredException(code);
        }
        return link.getLongUrl();
    }

    private void validate(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            boolean ok = uri.getHost() != null && scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
            if (!ok) {
                throw new InvalidUrlException("URL must be a valid http or https address");
            }
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL must be a valid http or https address");
        }
    }
    @Transactional(readOnly = true)
    public PageResponse<LinkSummary> listMine(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);          // 1..50, protects the DB

        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));      // newest first

        Page<LinkSummary> result = linkRepository.findByUserId(userId, pageable)
                .map(this::toSummary);

        return PageResponse.from(result);
    }

    private LinkSummary toSummary(Link link) {
        return new LinkSummary(
                link.getCode(),
                baseUrl + "/" + link.getCode(),
                link.getLongUrl(),
                link.getCreatedAt(),
                link.getExpiresAt(),
                link.isActive());       // use your getter name for the 'active' field
    }
    @Transactional
    public void delete(String code, Long userId) {
        Link link = linkRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));       // 404

        if (!link.getUserId().equals(userId)) {
            throw new LinkAccessDeniedException(code);                     // 403
        }
        linkRepository.delete(link);
    }
}