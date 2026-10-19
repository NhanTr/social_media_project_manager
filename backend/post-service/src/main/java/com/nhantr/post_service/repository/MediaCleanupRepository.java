package com.nhantr.post_service.repository;

import com.nhantr.post_service.entity.MediaCleanupItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MediaCleanupRepository extends JpaRepository<MediaCleanupItem, Long> {
    List<MediaCleanupItem> findTop25ByDoneAtIsNullAndScheduledAtBeforeOrderByScheduledAtAsc(Instant boundary);
}