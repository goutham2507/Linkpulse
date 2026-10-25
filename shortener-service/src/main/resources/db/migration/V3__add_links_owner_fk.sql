-- Dev cleanup: links created before login existed may point to a user id that doesn't exist
DELETE FROM links WHERE user_id NOT IN (SELECT id FROM users);

-- From now on every link must belong to a real user
ALTER TABLE links
    ADD CONSTRAINT fk_links_user FOREIGN KEY (user_id) REFERENCES users (id);