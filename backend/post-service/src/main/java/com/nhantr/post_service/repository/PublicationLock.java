package com.nhantr.post_service.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes publications for an account across processes until the surrounding transaction commits. */
@Repository
public class PublicationLock {
    private final JdbcTemplate jdbc;

    public PublicationLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(Long userId) {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", result -> { }, userId);
    }
}
