package com.nhantr.post_service.repository;

import com.nhantr.post_service.entity.MediaUpload;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MediaUploadRepository extends JpaRepository<MediaUpload, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from MediaUpload u where u.id = :id")
    Optional<MediaUpload> lockById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from MediaUpload u where u.status in :statuses and u.expiresAt < :boundary and u.consumedPostId is null order by u.id")
    List<MediaUpload> lockExpiredOrphans(List<MediaUpload.Status> statuses, Instant boundary);
}
