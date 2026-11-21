package com.nhantr.chat_service.repository;

import com.nhantr.chat_service.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByUserId1AndUserId2(Long userId1, Long userId2);

    @Query("""
            select c from Conversation c
            where c.userId1 = :userId or c.userId2 = :userId
            order by coalesce(c.lastMessageAt, c.createdAt) desc
            """)
    List<Conversation> findAllForUser(@Param("userId") Long userId);
}
