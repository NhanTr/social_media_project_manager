package com.nhantr.identity_service.repository;

import com.nhantr.identity_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.nhantr.identity_service.enums.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByPhoneLookupHashAndStatus(String phoneLookupHash, UserStatus status);

    Optional<User> findByPhoneLookupHash(String phoneLookupHash);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByFriendInviteCode(String friendInviteCode);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByGoogleId(String googleId);

    boolean existsByFriendInviteCode(String friendInviteCode);

    List<User> findAllByIdIn(Collection<Long> ids);

    @Query("""
            select u from User u
            where u.status = :status
              and (
                lower(u.username) like lower(concat('%', :keyword, '%'))
                or lower(coalesce(u.displayName, '')) like lower(concat('%', :keyword, '%'))
              )
            order by u.username
            """)
    List<User> searchByUsernameOrDisplayName(
            @Param("keyword") String keyword,
            @Param("status") UserStatus status
    );
}
