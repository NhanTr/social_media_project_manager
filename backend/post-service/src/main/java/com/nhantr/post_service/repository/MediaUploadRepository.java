package com.nhantr.post_service.repository;

import com.nhantr.post_service.entity.MediaUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MediaUploadRepository extends JpaRepository<MediaUpload, Long> {
    List<MediaUpload> findByStatusAndExpiresAtBefore(MediaUpload.Status status, Instant boundary);
}