package com.nhantr.identity_service.service;

import com.nhantr.identity_service.client.AccountMediaRetentionClient;
import com.nhantr.identity_service.entity.AccountDeletionRequest;
import com.nhantr.identity_service.enums.UserStatus;
import com.nhantr.identity_service.repository.AccountDeletionRequestRepository;
import com.nhantr.identity_service.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountRetentionService {
    private final AccountDeletionRequestRepository requests;
    private final UserRepository users;
    private final AccountMediaRetentionClient post;
    public AccountRetentionService(AccountDeletionRequestRepository requests, UserRepository users, AccountMediaRetentionClient post) {
        this.requests = requests; this.users = users; this.post = post;
    }
    @Transactional
    public String process(Long id, boolean dryRun) {
        var request = requests.lockById(id).orElseThrow();
        if (request.getStatus() != AccountDeletionRequest.Status.PENDING || request.getScheduledPurgeAt() == null
                || request.getScheduledPurgeAt().isAfter(Instant.now())) return "NOT_DUE";
        if (dryRun) return "DRY_RUN";
        // Idempotent owner API: partial batches or a lost response are safely replayed next run.
        if (!post.cleanup(request.getUserId())) return "MEDIA_PENDING";
        var user = users.findById(request.getUserId()).orElseThrow();
        user.setStatus(UserStatus.INACTIVE);
        user.setUsername("deleted-" + java.util.UUID.randomUUID());
        user.setDisplayName("Deleted account"); user.setEmail(null); user.setPhoneNumber(null);
        user.setPhoneLookupHash(null); user.setGoogleId(null); user.setAvatarUrl(null); user.setBio(null);
        user.setFriendInviteCode(null); user.setLastLoginAt(null); user.setDeletedAt(Instant.now());
        users.save(user);
        request.complete(); requests.save(request);
        return "COMPLETED";
    }
}
