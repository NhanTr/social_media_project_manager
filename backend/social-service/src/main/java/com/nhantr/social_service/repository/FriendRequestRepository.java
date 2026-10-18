package com.nhantr.social_service.repository;

import com.nhantr.social_service.entity.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FriendRequest r where r.id = :id")
    Optional<FriendRequest> findForUpdate(@Param("id") Long id);

    List<FriendRequest> findByReceiverIdAndStatusOrderByCreatedAtDesc(Long receiverId, FriendRequest.Status status);
    List<FriendRequest> findBySenderIdAndStatusOrderByCreatedAtDesc(Long senderId, FriendRequest.Status status);

    @Query("""
            select r from FriendRequest r
            where r.status = :status
              and ((r.senderId = :a and r.receiverId = :b) or (r.senderId = :b and r.receiverId = :a))
            """)
    Optional<FriendRequest> findPairByStatus(
            @Param("a") Long a,
            @Param("b") Long b,
            @Param("status") FriendRequest.Status status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from FriendRequest r
            where r.status = :status
              and ((r.senderId = :a and r.receiverId = :b) or (r.senderId = :b and r.receiverId = :a))
            """)
    List<FriendRequest> findAllPairByStatusForUpdate(
            @Param("a") Long a,
            @Param("b") Long b,
            @Param("status") FriendRequest.Status status
    );
}
