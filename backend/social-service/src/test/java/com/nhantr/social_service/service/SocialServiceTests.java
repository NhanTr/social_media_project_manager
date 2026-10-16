package com.nhantr.social_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.social_service.client.IdentityClient;
import com.nhantr.social_service.client.NotificationClient;
import com.nhantr.social_service.entity.FriendRequest;
import com.nhantr.social_service.repository.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialServiceTests {
    private final FriendRequestRepository requests = mock(FriendRequestRepository.class);
    private final SocialService service = new SocialService(requests, mock(FriendshipRepository.class),
            mock(BlockRepository.class), mock(IdentityClient.class), mock(NotificationClient.class));

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
}
