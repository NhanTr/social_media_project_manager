package com.nhantr.identity_service.service;

import com.nhantr.identity_service.entity.AccountDeletionRequest;
import com.nhantr.identity_service.client.AccountMediaRetentionClient;
import com.nhantr.identity_service.entity.User;
import com.nhantr.identity_service.repository.AccountDeletionRequestRepository;
import com.nhantr.identity_service.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountRetentionTests {
    @Test
    void retentionNeverPurgesBeforeGracePeriodOrWhileMediaRemainsPending() {
        var deletions = mock(AccountDeletionRequestRepository.class);
        var users = mock(UserRepository.class);
        var post = mock(AccountMediaRetentionClient.class);
        when(deletions.lockById(9L)).thenReturn(Optional.of(new AccountDeletionRequest(1L, Instant.now().plusSeconds(60))));
        var service = new AccountRetentionService(deletions, users, post);
        assertEquals("NOT_DUE", service.process(9L, false));
        verifyNoInteractions(users, post);
        when(deletions.lockById(9L)).thenReturn(Optional.of(new AccountDeletionRequest(1L, Instant.EPOCH)));
        when(post.cleanup(1L)).thenReturn(false);
        assertEquals("MEDIA_PENDING", service.process(9L, false));
        verifyNoInteractions(users);
    }

    @Test
    void dryRunNeverCallsCleanupOrMutatesIdentity() {
        var deletions = mock(AccountDeletionRequestRepository.class);
        var users = mock(UserRepository.class);
        var post = mock(AccountMediaRetentionClient.class);
        var request = new AccountDeletionRequest(1L, Instant.EPOCH);
        when(deletions.lockById(9L)).thenReturn(Optional.of(request));
        var service = new AccountRetentionService(deletions, users, post);
        assertEquals("DRY_RUN", service.process(9L, true));
        assertEquals(AccountDeletionRequest.Status.PENDING, request.getStatus());
        verifyNoInteractions(users, post);
    }
    @Test
    void downstreamFailureLeavesIdentityAndPendingRequestRecoverable() {
        var deletions = mock(AccountDeletionRequestRepository.class);
        var users = mock(UserRepository.class);
        var post = mock(AccountMediaRetentionClient.class);
        var request = new AccountDeletionRequest(1L, Instant.EPOCH);
        when(deletions.lockById(9L)).thenReturn(Optional.of(request));
        when(post.cleanup(1L)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> new AccountRetentionService(deletions, users, post).process(9L, false));
        assertEquals(AccountDeletionRequest.Status.PENDING, request.getStatus());
        verifyNoInteractions(users);
    }
    @Test
    void completionTombstonesPiiWithoutBreakingForeignKeys() {
        var deletions = mock(AccountDeletionRequestRepository.class);
        var users = mock(UserRepository.class);
        var post = mock(AccountMediaRetentionClient.class);
        var request = new AccountDeletionRequest(1L, Instant.EPOCH);
        var user = new User(); user.setId(1L); user.setEmail("private@example.com");
        when(deletions.lockById(9L)).thenReturn(Optional.of(request));
        when(post.cleanup(1L)).thenReturn(true);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        assertEquals("COMPLETED", new AccountRetentionService(deletions, users, post).process(9L, false));
        assertNull(user.getEmail()); assertNotNull(user.getDeletedAt());
        assertEquals(AccountDeletionRequest.Status.COMPLETED, request.getStatus());
    }
}
