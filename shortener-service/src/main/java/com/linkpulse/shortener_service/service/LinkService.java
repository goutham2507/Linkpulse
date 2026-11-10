package com.linkpulse.shortener_service.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkpulse.shortener_service.cache.LinkCache;
import com.linkpulse.shortener_service.dto.CreateLinkRequest;
import com.linkpulse.shortener_service.dto.CreateLinkResponse;
import com.linkpulse.shortener_service.entity.Link;
import com.linkpulse.shortener_service.exception.AliasAlreadyTakenException;
import com.linkpulse.shortener_service.exception.InvalidUrlException;
import com.linkpulse.shortener_service.exception.LinkDisabledException;
import com.linkpulse.shortener_service.exception.LinkExpiredException;
import com.linkpulse.shortener_service.exception.LinkNotFoundException;
import com.linkpulse.shortener_service.exception.RateLimitExceededException;
import com.linkpulse.shortener_service.ratelimit.RateLimitResult;
import com.linkpulse.shortener_service.ratelimit.RateLimiter;
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

    private static final Pattern VALID_CODE = Pattern.compile("^[a-zA-Z0-9_-]{1,20}$");

    private final LinkRepository linkRepository;
    private final LinkCache linkCache;
    private final RateLimiter rateLimiter;
    private final String baseUrl;
    private final int createLimit;
    private final Duration createWindow;

    public LinkService(LinkRepository linkRepository,
                       LinkCache linkCache,
                       RateLimiter rateLimiter,
                       @Value("${app.base-url}") String baseUrl,
                       @Value("${app.rate-limit.create-link.limit:10}") int createLimit,
                       @Value("${app.rate-limit.create-link.window-seconds:60}") long createWindowSeconds) {
        this.linkRepository = linkRepository;
        this.linkCache = linkCache;
        this.rateLimiter = rateLimiter;
        this.baseUrl = baseUrl;
        this.createLimit = createLimit;
        this.createWindow = Duration.ofSeconds(createWindowSeconds);
    }

    @Transactional
    public CreateLinkResponse create(CreateLinkRequest request, Long userId) {
        // first thing, so a throttled caller costs us no validation or DB work
        RateLimitResult rate = rateLimiter.tryAcquire("create", "user:" + userId, createLimit, createWindow);
        if (!rate.allowed()) {
            throw new RateLimitExceededException(rate.retryAfterSeconds());
        }

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

        linkCache.evictAfterCommit(link.getCode());   // clears a negative marker so a new link works immediately

        return new CreateLinkResponse(link.getCode(), baseUrl + "/" + link.getCode(), link.getLongUrl());
    }

    // no @Transactional: a cache hit must not borrow a DB connection (the repository call opens its own)
    public String resolve(String code) {
        // malformed codes can't exist, so reject them before they can fill Redis with junk keys or hit the DB
        if (!VALID_CODE.matcher(code).matches()) {
            throw new LinkNotFoundException(code);
        }

        var cached = linkCache.get(code);
        if (cached.isPresent()) {
            if (LinkCache.NOT_FOUND.equals(cached.get())) {
                throw new LinkNotFoundException(code);   // known-missing: absorb repeated probes without the DB
            }
            return cached.get();
        }

        Link link = linkRepository.findByCode(code)
                .orElseThrow(() -> {
                    linkCache.putNotFound(code);   // remember the miss so repeated unknown codes skip the DB
                    return new LinkNotFoundException(code);
                });

        // checked before expiry: an admin takedown applies regardless of the expiry date
        if (!link.isActive()) {
            throw new LinkDisabledException(code);
        }
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new LinkExpiredException(code);
        }
        // only redirectable links are cached, so disabled/expired ones always hit the DB checks
        linkCache.put(code, link.getLongUrl(), link.getExpiresAt());
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
    public void adminDisable(String code) {
        Link link = linkRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));       // 404
        link.disable();     // no save(): the managed entity is flushed at commit (dirty checking)
        linkCache.evictAfterCommit(code);   // otherwise the disabled link keeps redirecting until the TTL expires
    }

    @Transactional
    public void adminEnable(String code) {
        Link link = linkRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));       // 404
        link.enable();      // no save(): dirty checking
        linkCache.evictAfterCommit(code);   // keep the cache consistent with the DB state
    }

    @Transactional
    public void delete(String code, Long userId) {
        Link link = linkRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));       // 404

        if (!link.getUserId().equals(userId)) {
            throw new LinkAccessDeniedException(code);                     // 403
        }
        linkRepository.delete(link);
        linkCache.evictAfterCommit(code);   // a deleted link must stop redirecting immediately
    }

    @Transactional
    public void updateExpiry(String code, Long userId, LocalDateTime newExpiresAt) {
        Link link = linkRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException(code));       // 404

        if (!link.getUserId().equals(userId)) {
            throw new LinkAccessDeniedException(code);                     // 403
        }
        link.setExpiresAt(newExpiresAt);    // no save(): dirty checking
        linkCache.evictAfterCommit(code);   // the cached TTL was capped at the old expiry
    }
}
