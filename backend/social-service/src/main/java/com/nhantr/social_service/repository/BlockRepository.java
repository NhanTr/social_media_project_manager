package com.nhantr.social_service.repository;

import com.nhantr.social_service.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {
    Optional<Block> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
    List<Block> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

    @Query("""
            select case when count(b) > 0 then true else false end from Block b
            where (b.blockerId = :a and b.blockedId = :b) or (b.blockerId = :b and b.blockedId = :a)
            """)
    boolean existsEitherDirection(@Param("a") Long a, @Param("b") Long b);
}
