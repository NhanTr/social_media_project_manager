package com.nhantr.social_service.repository;

import com.nhantr.social_service.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    @Query("select f from Friendship f where f.userIdA = :id or f.userIdB = :id order by f.createdAt desc")
    List<Friendship> findAllForUser(@Param("id") Long userId);

    Optional<Friendship> findByUserIdAAndUserIdB(Long userIdA, Long userIdB);
    boolean existsByUserIdAAndUserIdB(Long userIdA, Long userIdB);
}
