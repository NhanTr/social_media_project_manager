package com.nhantr.chat_service.repository;

import com.nhantr.chat_service.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderBySentAtDescIdDesc(Long conversationId, Pageable pageable);

    @Query("""
            select m from Message m
            where m.conversationId = :conversationId
              and (m.sentAt < :cursorTime or (m.sentAt = :cursorTime and m.id < :cursorId))
            order by m.sentAt desc, m.id desc
            """)
    List<Message> pageAfter(
            @Param("conversationId") Long conversationId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Modifying
    @Query("""
            update Message m set m.status = :readStatus, m.readAt = :now
            where m.conversationId = :conversationId and m.senderId <> :readerId
              and m.status <> :readStatus
            """)
    int markRead(
            @Param("conversationId") Long conversationId,
            @Param("readerId") Long readerId,
            @Param("readStatus") Message.Status readStatus,
            @Param("now") Instant now
    );
}
