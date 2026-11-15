package com.nhantr.post_service.repository;

import com.nhantr.post_service.entity.MediaCleanupItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface MediaCleanupRepository extends JpaRepository<MediaCleanupItem, Long> {
    boolean existsByObjectKeyStartingWithAndDoneAtIsNull(String prefix);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO media_cleanup_queue (object_key, scheduled_at, attempts)
            VALUES (:objectKey, :scheduledAt, 0)
            ON CONFLICT (object_key) DO UPDATE SET
              scheduled_at = LEAST(media_cleanup_queue.scheduled_at, EXCLUDED.scheduled_at),
              done_at = NULL, attempts = 0, last_error = NULL
            """, nativeQuery = true)
    void enqueue(String objectKey, Instant scheduledAt);

    @Query(value = "SELECT * FROM media_cleanup_queue WHERE done_at IS NULL AND attempts < 5 AND scheduled_at < :boundary ORDER BY scheduled_at LIMIT 25", nativeQuery = true)
    List<MediaCleanupItem> findTop25ByDoneAtIsNullAndScheduledAtBeforeOrderByScheduledAtAsc(Instant boundary);
}