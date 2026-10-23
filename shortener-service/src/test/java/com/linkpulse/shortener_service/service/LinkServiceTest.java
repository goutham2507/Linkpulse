package com.linkpulse.shortener_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.linkpulse.shortener_service.dto.CreateLinkRequest;
import com.linkpulse.shortener_service.dto.CreateLinkResponse;
import com.linkpulse.shortener_service.entity.Link;
import com.linkpulse.shortener_service.exception.AliasAlreadyTakenException;
import com.linkpulse.shortener_service.exception.InvalidUrlException;
import com.linkpulse.shortener_service.exception.LinkExpiredException;
import com.linkpulse.shortener_service.exception.LinkNotFoundException;
import com.linkpulse.shortener_service.repository.LinkRepository;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String LONG_URL = "https://example.com/a/very/long/path";

    @Mock
    private LinkRepository linkRepository;

    private LinkService linkService;

    @BeforeEach
    void setUp() {
        linkService = new LinkService(linkRepository, BASE_URL);
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

        CreateLinkResponse res = linkService.create(new CreateLinkRequest(LONG_URL, null, null));

        assertEquals("g8", res.code());                       // Base62(1000)
        assertEquals(BASE_URL + "/g8", res.shortUrl());
        assertEquals(LONG_URL, res.longUrl());
    }

    @Test
    void create_withFreeAlias_usesTheAlias() {
        when(linkRepository.existsByCode("myshoes")).thenReturn(false);
        when(linkRepository.save(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateLinkResponse res = linkService.create(new CreateLinkRequest(LONG_URL, "myshoes", null));

        assertEquals("myshoes", res.code());
        assertEquals(BASE_URL + "/myshoes", res.shortUrl());
    }

    @Test
    void create_withTakenAlias_throwsAndSavesNothing() {
        when(linkRepository.existsByCode("myshoes")).thenReturn(true);

        assertThrows(AliasAlreadyTakenException.class,
                () -> linkService.create(new CreateLinkRequest(LONG_URL, "myshoes", null)));

        verify(linkRepository, never()).save(any());
    }

    @Test
    void create_withNonHttpUrl_throwsInvalidUrl() {
        assertThrows(InvalidUrlException.class,
                () -> linkService.create(new CreateLinkRequest("ftp://example.com/file", null, null)));

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
}