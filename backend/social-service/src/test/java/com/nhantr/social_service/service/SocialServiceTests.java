package com.nhantr.social_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.social_service.client.IdentityClient;
import com.nhantr.social_service.client.NotificationClient;
import com.nhantr.social_service.entity.Block;
import com.nhantr.social_service.entity.FriendRequest;
import com.nhantr.social_service.entity.Friendship;
import com.nhantr.social_service.repository.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialServiceTests {
    private final FriendRequestRepository requests = mock(FriendRequestRepository.class);
    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final BlockRepository blocks = mock(BlockRepository.class);
    private final IdentityClient identity = mock(IdentityClient.class);
    private final SocialService service = new SocialService(
            requests,
            friendships,
            blocks,
            identity,
            mock(NotificationClient.class)
    );

    @Test void senderCanCancelAndRetryWithoutChangingOutcome() {
        var request = new FriendRequest(1L, 2L);
        when(requests.findForUpdate(10L)).thenReturn(Optional.of(request));
        when(requests.save(request)).thenReturn(request);
        assertThat(service.cancel(1L, 10L).status()).isEqualTo("CANCELLED");
        assertThat(service.cancel(1L, 10L).status()).isEqualTo("CANCELLED");
        assertThat(request.getStatus()).isEqualTo(FriendRequest.Status.CANCELLED);
    }

    @Test void receiverCannotCancelAndAcceptedRequestCannotBeCancelled() {
        var request = new FriendRequest(1L, 2L);
        when(requests.findForUpdate(10L)).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.cancel(2L, 10L)).isInstanceOf(ApiException.class);
        assertThat(request.getStatus()).isEqualTo(FriendRequest.Status.PENDING);
        request.setStatus(FriendRequest.Status.ACCEPTED);
        assertThatThrownBy(() -> service.cancel(1L, 10L)).isInstanceOf(ApiException.class);
        assertThat(request.getStatus()).isEqualTo(FriendRequest.Status.ACCEPTED);
    }

    @Test void blockLocksAndDeclinesAllPendingRequestsAndRemovesFriendship() {
        FriendRequest sent = new FriendRequest(1L, 2L);
        FriendRequest received = new FriendRequest(2L, 1L);
        Friendship friendship = new Friendship(1L, 2L);
        Block block = new Block(1L, 2L);
        when(identity.exists(2L)).thenReturn(true);
        when(blocks.findByBlockerIdAndBlockedId(1L, 2L)).thenReturn(Optional.empty());
        when(blocks.save(any(Block.class))).thenReturn(block);
        when(friendships.findByUserIdAAndUserIdB(1L, 2L)).thenReturn(Optional.of(friendship));
        when(requests.findAllPairByStatusForUpdate(1L, 2L, FriendRequest.Status.PENDING))
                .thenReturn(List.of(sent, received));
        when(requests.save(any(FriendRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.block(1L, 2L);

        verify(requests).findAllPairByStatusForUpdate(1L, 2L, FriendRequest.Status.PENDING);
        verify(friendships).delete(friendship);
        assertThat(sent.getStatus()).isEqualTo(FriendRequest.Status.DECLINED);
        assertThat(received.getStatus()).isEqualTo(FriendRequest.Status.DECLINED);
        verify(requests).save(sent);
        verify(requests).save(received);
    }

    @Test void repeatedBlockDoesNotCreateDuplicateBlock() {
        Block existing = new Block(1L, 2L);
        when(identity.exists(2L)).thenReturn(true);
        when(blocks.findByBlockerIdAndBlockedId(1L, 2L)).thenReturn(Optional.of(existing));
        when(requests.findAllPairByStatusForUpdate(1L, 2L, FriendRequest.Status.PENDING))
                .thenReturn(List.of());

        service.block(1L, 2L);
        service.block(1L, 2L);

        verify(blocks, never()).save(any());
        verify(requests, times(2))
                .findAllPairByStatusForUpdate(1L, 2L, FriendRequest.Status.PENDING);
    }
}
