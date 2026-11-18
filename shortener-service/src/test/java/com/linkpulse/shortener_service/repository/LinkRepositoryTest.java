package com.linkpulse.shortener_service.repository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import com.linkpulse.shortener_service.entity.Link;

@SpringBootTest
@Transactional                      // rolls back after the test, so no leftover rows
class LinkRepositoryTest {

    @Autowired LinkRepository linkRepository;
    @Autowired EntityManager entityManager;

    @Test
    void saveAndFindByCode() {
        // links.user_id has a foreign key, so create the owner here instead of assuming user 1 exists
        Long userId = ((Number) entityManager.createNativeQuery(
                "INSERT INTO users (email, password_hash) VALUES ('repo-test@example.com', 'x') RETURNING id")
                .getSingleResult()).longValue();

        Link saved = linkRepository.save(
                new Link("abc123", "https://example.com/some/long/url", userId));

        entityManager.flush();      // push the INSERT to the database
        entityManager.clear();      // forget cached objects, so the next read hits the DB

        Link found = linkRepository.findByCode("abc123").orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals("https://example.com/some/long/url", found.getLongUrl());
        assertTrue(found.isActive());
        assertNotNull(found.getCreatedAt());
    }
}