package com.nhantr.identity_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.identity_service.entity.AccountDeletionRequest;
import com.nhantr.identity_service.enums.UserStatus;
import com.nhantr.identity_service.repository.AccountDeletionRequestRepository;
import com.nhantr.identity_service.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Service
public class AccountService {
    private final AccountDeletionRequestRepository deletionRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redis;

    public AccountService(
            AccountDeletionRequestRepository deletionRepository,
            UserRepository userRepository,
            StringRedisTemplate redis
    ) {
        this.deletionRepository = deletionRepository;
        this.userRepository = userRepository;
        this.redis = redis;
    }

    @Transactional
    public DeletionResponse requestDeletion(Long userId) {
        if (deletionRepository.existsByUserIdAndStatus(userId, AccountDeletionRequest.Status.PENDING)) {
            throw ApiException.conflict("DELETION_ALREADY_REQUESTED", "Account deletion is already pending.");
        }
        var user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found."));
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        Instant purgeAt = Instant.now().plus(30, ChronoUnit.DAYS);
        var deletion = deletionRepository.save(new AccountDeletionRequest(userId, purgeAt));
        Set<String> keys = redis.keys("identity:refresh-token:" + userId + ":*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
        return new DeletionResponse(deletion.getId(), deletion.getStatus().name(), deletion.getScheduledPurgeAt());
    }

    public record DeletionResponse(Long requestId, String status, Instant scheduledPurgeAt) {}
}
