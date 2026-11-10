package com.linkpulse.shortener_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.linkpulse.shortener_service.cache.LinkCache;
import com.linkpulse.shortener_service.dto.CreateLinkRequest;
import com.linkpulse.shortener_service.dto.CreateLinkResponse;
import com.linkpulse.shortener_service.entity.Link;
import com.linkpulse.shortener_service.exception.AliasAlreadyTakenException;
import com.linkpulse.shortener_service.exception.InvalidUrlException;
import com.linkpulse.shortener_service.exception.LinkAccessDeniedException;
import com.linkpulse.shortener_service.exception.LinkDisabledException;
import com.linkpulse.shortener_service.exception.LinkExpiredException;
import com.linkpulse.shortener_service.exception.LinkNotFoundException;
import com.linkpulse.shortener_service.exception.RateLimitExceededException;
import com.linkpulse.shortener_service.ratelimit.RateLimitResult;
import com.linkpulse.shortener_service.ratelimit.RateLimiter;
import com.linkpulse.shortener_service.repository.LinkRepository;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String LONG_URL = "https://example.com/a/very/long/path";

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private LinkCache linkCache;

    @Mock
    private RateLimiter rateLimiter;

    private LinkService linkService;

    @BeforeEach
    void setUp() {
        // lenient: most tests don't care about the limiter, so an unused default stub must not fail them
        lenient().when(rateLimiter.tryAcquire(anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimitResult(true, 60));
        linkService = new LinkService(linkRepository, linkCache, rateLimiter, BASE_URL, 10, 60);
    }

    @Test
    void create_overRateLimit_throws429AndSavesNothing() {
        when(rateLimiter.tryAcquire(eq("create"), eq("user:1"), eq(10), eq(Duration.ofSeconds(60))))
                .thenReturn(new RateLimitResult(false, 42));

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> linkService.create(new CreateLinkRequest(LONG_URL, null, null), 1L));

        assertEquals(42, ex.getRetryAfterSeconds());
        verify(linkRepository, never()).save(any());
        verify(linkRepository, never()).existsByCode(any());
    }

    // ---------- create ----------

    @Test
    void create_withoutAlias_usesBase62OfGeneratedId() {
        // fake DB: assigns id 1000 on save (the real DB does this itself)
        when(linkRepository.save(any(Link.class))).thenAnswer(inv -> {
            Link link = inv.getArgument(0);
            ReflectionTestUtils.setField(link, "id", 1000L);
            return link;
        });

        CreateLinkResponse res = linkService.create(new CreateLinkRequest(LONG_URL, null, null), 1L);

        assertEquals("g8", res.code());                       // Base62(1000)
        assertEquals(BASE_URL + "/g8", res.shortUrl());
        assertEquals(LONG_URL, res.longUrl());
    }

    @Test
    void create_withFreeAlias_usesTheAlias() {
        when(linkRepository.existsByCode("myshoes")).thenReturn(false);
        when(linkRepository.save(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateLinkResponse res = linkService.create(new CreateLinkRequest(LONG_URL, "myshoes", null), 1L);

        assertEquals("myshoes", res.code());
        assertEquals(BASE_URL + "/myshoes", res.shortUrl());
    }

    @Test
    void create_withTakenAlias_throwsAndSavesNothing() {
        when(linkRepository.existsByCode("myshoes")).thenReturn(true);

        assertThrows(AliasAlreadyTakenException.class,
                () -> linkService.create(new CreateLinkRequest(LONG_URL, "myshoes", null), 1L));

        verify(linkRepository, never()).save(any());
    }

    @Test
    void create_withNonHttpUrl_throwsInvalidUrl() {
        assertThrows(InvalidUrlException.class,
                () -> linkService.create(new CreateLinkRequest("ftp://example.com/file", null, null), 1L));

        verify(linkRepository, never()).save(any());
    }

    // ---------- resolve ----------

    @Test
    void resolve_unknownCode_throwsNotFound() {
        when(linkRepository.findByCode("zzzz")).thenReturn(Optional.empty());

        assertThrows(LinkNotFoundException.class, () -> linkService.resolve("zzzz"));
    }

    @Test
    void resolve_expiredLink_throwsExpired() {
        Link link = new Link("old1", LONG_URL, 1L);
        link.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(linkRepository.findByCode("old1")).thenReturn(Optional.of(link));

        assertThrows(LinkExpiredException.class, () -> linkService.resolve("old1"));
    }

    @Test
    void resolve_activeLink_returnsLongUrl() {
        Link link = new Link("live", LONG_URL, 1L);
        link.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(linkRepository.findByCode("live")).thenReturn(Optional.of(link));

        assertEquals(LONG_URL, linkService.resolve("live"));
    }

    @Test
    void resolve_linkWithoutExpiry_returnsLongUrl() {
        Link link = new Link("forever", LONG_URL, 1L);   // expiresAt stays null
        when(linkRepository.findByCode("forever")).thenReturn(Optional.of(link));

        assertEquals(LONG_URL, linkService.resolve("forever"));
    }
    @Test
    void delete_byOwner_deletesLink() {
        Link link = new Link("mine", LONG_URL, 1L);
        when(linkRepository.findByCode("mine")).thenReturn(Optional.of(link));

        linkService.delete("mine", 1L);

        verify(linkRepository).delete(link);
    }

    @Test
    void delete_byOtherUser_throwsForbidden() {
        Link link = new Link("mine", LONG_URL, 1L);
        when(linkRepository.findByCode("mine")).thenReturn(Optional.of(link));

        assertThrows(LinkAccessDeniedException.class, () -> linkService.delete("mine", 2L));
        verify(linkRepository, never()).delete(any());
    }

    @Test
    void resolve_disabledLink_throwsDisabled() {
        Link link = new Link("off", LONG_URL, 1L);
        link.disable();
        when(linkRepository.findByCode("off")).thenReturn(Optional.of(link));

        assertThrows(LinkDisabledException.class, () -> linkService.resolve("off"));
    }

    @Test
    void adminDisable_marksLinkInactive() {
        Link link = new Link("on", LONG_URL, 1L);
        when(linkRepository.findByCode("on")).thenReturn(Optional.of(link));

        linkService.adminDisable("on");

        assertFalse(link.isActive());
        verify(linkRepository, never()).save(any());
    }

    @Test
    void resolve_cacheHit_doesNotQueryDatabase() {
        when(linkCache.get("hot")).thenReturn(Optional.of(LONG_URL));

        assertEquals(LONG_URL, linkService.resolve("hot"));

        verify(linkRepository, never()).findByCode(any());
    }

    @Test
    void resolve_cacheMiss_loadsFromDbAndCaches() {
        Link link = new Link("cold", LONG_URL, 1L);
        when(linkCache.get("cold")).thenReturn(Optional.empty());
        when(linkRepository.findByCode("cold")).thenReturn(Optional.of(link));

        assertEquals(LONG_URL, linkService.resolve("cold"));

        verify(linkCache).put("cold", LONG_URL, null);
    }

    @Test
    void resolve_disabledLink_isNotCached() {
        Link link = new Link("off", LONG_URL, 1L);
        link.disable();
        when(linkCache.get("off")).thenReturn(Optional.empty());
        when(linkRepository.findByCode("off")).thenReturn(Optional.of(link));

        assertThrows(LinkDisabledException.class, () -> linkService.resolve("off"));

        verify(linkCache, never()).put(any(), any(), any());
    }

    @Test
    void resolve_unknownCode_cachesNotFound() {
        when(linkCache.get("ghost")).thenReturn(Optional.empty());
        when(linkRepository.findByCode("ghost")).thenReturn(Optional.empty());

        assertThrows(LinkNotFoundException.class, () -> linkService.resolve("ghost"));

        verify(linkCache).putNotFound("ghost");
    }

    @Test
    void resolve_negativeCacheHit_doesNotQueryDatabase() {
        when(linkCache.get("ghost")).thenReturn(Optional.of(LinkCache.NOT_FOUND));

        assertThrows(LinkNotFoundException.class, () -> linkService.resolve("ghost"));

        verify(linkRepository, never()).findByCode(any());
    }

    @Test
    void resolve_invalidCodeFormat_neverTouchesCacheOrDatabase() {
        assertThrows(LinkNotFoundException.class, () -> linkService.resolve("bad code!"));
        assertThrows(LinkNotFoundException.class, () -> linkService.resolve("a".repeat(21)));

        verify(linkCache, never()).get(any());
        verify(linkRepository, never()).findByCode(any());
    }

    @Test
    void create_withAlias_clearsNegativeCacheMarker() {
        when(linkRepository.existsByCode("later01")).thenReturn(false);
        when(linkRepository.save(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));

        linkService.create(new CreateLinkRequest(LONG_URL, "later01", null), 1L);

        verify(linkCache).evictAfterCommit("later01");
    }

    @Test
    void delete_unknownCode_throwsNotFound() {
        when(linkRepository.findByCode("nope")).thenReturn(Optional.empty());

        assertThrows(LinkNotFoundException.class, () -> linkService.delete("nope", 1L));
    }

    @Test
    void delete_byOwner_evictsCache() {
        Link link = new Link("mine", LONG_URL, 1L);
        when(linkRepository.findByCode("mine")).thenReturn(Optional.of(link));

        linkService.delete("mine", 1L);

        verify(linkCache).evictAfterCommit("mine");
    }

    @Test
    void adminDisable_evictsCache() {
        Link link = new Link("on", LONG_URL, 1L);
        when(linkRepository.findByCode("on")).thenReturn(Optional.of(link));

        linkService.adminDisable("on");

        verify(linkCache).evictAfterCommit("on");
    }

    @Test
    void updateExpiry_byOwner_updatesAndEvicts() {
        Link link = new Link("mine", LONG_URL, 1L);
        LocalDateTime newExpiry = LocalDateTime.now().plusDays(2);
        when(linkRepository.findByCode("mine")).thenReturn(Optional.of(link));

        linkService.updateExpiry("mine", 1L, newExpiry);

        assertEquals(newExpiry, link.getExpiresAt());
        verify(linkCache).evictAfterCommit("mine");
    }

    @Test
    void updateExpiry_byOtherUser_throwsForbidden() {
        Link link = new Link("mine", LONG_URL, 1L);
        when(linkRepository.findByCode("mine")).thenReturn(Optional.of(link));

        assertThrows(LinkAccessDeniedException.class,
                () -> linkService.updateExpiry("mine", 2L, LocalDateTime.now().plusDays(1)));
        verify(linkCache, never()).evictAfterCommit(any());
    }
}
