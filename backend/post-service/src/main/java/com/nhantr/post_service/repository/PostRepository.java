package com.nhantr.post_service.repository;

import com.nhantr.post_service.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    Optional<Post> findByIdAndStatus(Long id, Post.Status status);

    @Query("""
            select p from Post p
            where p.userId in :userIds and p.visibility = :visibility and p.status = :status
            order by p.createdAt desc, p.id desc
            """)
    List<Post> feed(
            @Param("userIds") Collection<Long> userIds,
            @Param("visibility") Post.Visibility visibility,
            @Param("status") Post.Status status,
            Pageable pageable
    );

    @Query("""
            select p from Post p
            where p.userId in :userIds and p.visibility = :visibility and p.status = :status
              and (p.createdAt < :cursorTime or (p.createdAt = :cursorTime and p.id < :cursorId))
            order by p.createdAt desc, p.id desc
            """)
    List<Post> feedAfter(
            @Param("userIds") Collection<Long> userIds,
            @Param("visibility") Post.Visibility visibility,
            @Param("status") Post.Status status,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            select p from Post p
            where p.userId = :userId and p.status = :status
            order by p.createdAt desc, p.id desc
            """)
    List<Post> byUser(
            @Param("userId") Long userId,
            @Param("status") Post.Status status,
            Pageable pageable
    );

    @Query("""
            select p from Post p
            where p.userId = :userId and p.status = :status
              and (p.createdAt < :cursorTime or (p.createdAt = :cursorTime and p.id < :cursorId))
            order by p.createdAt desc, p.id desc
            """)
    List<Post> byUserAfter(
            @Param("userId") Long userId,
            @Param("status") Post.Status status,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
