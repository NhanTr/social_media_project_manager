package com.nhantr.identity_service.dto.response;

import com.nhantr.identity_service.entity.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        String phoneNumber,
        String displayName,
        String avatarUrl,
        String bio,
        String status,
        String authProvider,
        String friendInviteCode,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getStatus().name(),
                user.getAuthProvider().name(),
                user.getFriendInviteCode(),
                user.getCreatedAt()
        );
    }
}
