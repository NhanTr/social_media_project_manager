package com.nhantr.social_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.social_service.client.IdentityClient;
import com.nhantr.social_service.client.NotificationClient;
import com.nhantr.social_service.entity.FriendRequest;
import com.nhantr.social_service.repository.BlockRepository;
import com.nhantr.social_service.repository.FriendRequestRepository;
import com.nhantr.social_service.repository.FriendshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialServiceCancelTests {

    private FriendRequestRepository requestRepository;
    private SocialService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        requestRepository = mock(FriendRequestRepository.class);
        service = new SocialService(
                requestRepository,
                mock(FriendshipRepository.class),
                mock(BlockRepository.class),
                mock(IdentityClient.class),
                mock(NotificationClient.class)
        );
    }

    private FriendRequest request(Long id, Long senderId, FriendRequest.Status status) {
        FriendRequest request = mock(FriendRequest.class);
        when(request.getId()).thenReturn(id);
        when(request.getSenderId()).thenReturn(senderId);
        when(request.getReceiverId()).thenReturn(99L);
        when(request.getStatus()).thenReturn(status);
        when(request.getCreatedAt()).thenReturn(Instant.parse("2026-10-16T08:00:00Z"));
        return request;
    }

    @Test
    void cancelRemovesPendingRequestOwnedBySender() {
        FriendRequest pending = request(7L, 1L, FriendRequest.Status.PENDING);
        when(requestRepository.findById(7L)).thenReturn(Optional.of(pending));

        SocialService.RequestResponse response = service.cancel(1L, 7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(requestRepository).delete(pending);
    }

    @Test
    void cancelRejectsNonSender() {
        FriendRequest pending = request(7L, 2L, FriendRequest.Status.PENDING);
        when(requestRepository.findById(7L)).thenReturn(Optional.of(pending));

        ApiException error = catchThrowableOfType(() -> service.cancel(1L, 7L), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("FRIEND_REQUEST_FORBIDDEN");
        assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(requestRepository, never()).delete(pending);
    }

    @Test
    void cancelRejectsRequestThatIsNoLongerPending() {
        FriendRequest accepted = request(7L, 1L, FriendRequest.Status.ACCEPTED);
        when(requestRepository.findById(7L)).thenReturn(Optional.of(accepted));

        ApiException error = catchThrowableOfType(() -> service.cancel(1L, 7L), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("FRIEND_REQUEST_CLOSED");
        assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
        verify(requestRepository, never()).delete(accepted);
    }

    @Test
    void cancelThrowsWhenRequestDoesNotExist() {
        when(requestRepository.findById(7L)).thenReturn(Optional.empty());

        ApiException error = catchThrowableOfType(() -> service.cancel(1L, 7L), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("FRIEND_REQUEST_NOT_FOUND");
        assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(requestRepository, never()).delete(any(FriendRequest.class));
    }
}