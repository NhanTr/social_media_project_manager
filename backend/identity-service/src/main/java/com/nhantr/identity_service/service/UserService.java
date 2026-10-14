package com.nhantr.identity_service.service;

import com.nhantr.identity_service.dto.response.UserResponse;
import com.nhantr.identity_service.dto.request.UpdateUserRequest;
import com.nhantr.identity_service.enums.UserStatus;
import com.nhantr.identity_service.repository.UserRepository;
import com.nhantr.common.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse getMe(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found."));
    }

    @Transactional
    public UserResponse updateMe(Long userId, UpdateUserRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found."));
        if (request.username() != null && !request.username().equalsIgnoreCase(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw ApiException.conflict("USERNAME_TAKEN", "Username is already in use.");
            }
            user.setUsername(request.username().toLowerCase());
        }
        if (request.displayName() != null) user.setDisplayName(request.displayName().trim());
        if (request.avatarUrl() != null) user.setAvatarUrl(request.avatarUrl().trim());
        if (request.bio() != null) user.setBio(request.bio().trim());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse changeStatus(Long userId, UserStatus status) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found."));
        user.setStatus(status);
        return UserResponse.from(userRepository.save(user));
    }
}
