package com.nhantr.social_service.service;

import com.nhantr.common.api.ApiException;
import com.nhantr.social_service.client.IdentityClient;
import com.nhantr.social_service.client.NotificationClient;
import com.nhantr.social_service.entity.Block;
import com.nhantr.social_service.entity.FriendRequest;
import com.nhantr.social_service.entity.Friendship;
import com.nhantr.social_service.repository.BlockRepository;
import com.nhantr.social_service.repository.FriendRequestRepository;
import com.nhantr.social_service.repository.FriendshipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SocialService {
    private final FriendRequestRepository requestRepository;
    private final FriendshipRepository friendshipRepository;
    private final BlockRepository blockRepository;
    private final IdentityClient identityClient;
    private final NotificationClient notificationClient;

    public SocialService(
            FriendRequestRepository requestRepository,
            FriendshipRepository friendshipRepository,
            BlockRepository blockRepository,
            IdentityClient identityClient,
            NotificationClient notificationClient
    ) {
        this.requestRepository = requestRepository;
        this.friendshipRepository = friendshipRepository;
        this.blockRepository = blockRepository;
        this.identityClient = identityClient;
        this.notificationClient = notificationClient;
    }

    public List<SearchResult> search(Long currentUserId, String keyword) {
        return identityClient.search(keyword).stream()
                .filter(user -> !user.id().equals(currentUserId))
                .map(user -> new SearchResult(
                        user,
                        areFriends(currentUserId, user.id()),
                        isBlocked(currentUserId, user.id()),
                        pendingPair(currentUserId, user.id()).map(FriendRequest::getId).orElse(null)
                ))
                .toList();
    }

    public List<IdentityClient.UserSummary> friends(Long userId) {
        return identityClient.batch(friendIds(userId));
    }

    public List<Long> friendIds(Long userId) {
        return friendshipRepository.findAllForUser(userId).stream().map(f -> f.other(userId)).toList();
    }

    public List<RequestResponse> incoming(Long userId) {
        return requestRepository.findByReceiverIdAndStatusOrderByCreatedAtDesc(userId, FriendRequest.Status.PENDING)
                .stream().map(RequestResponse::from).toList();
    }

    public List<RequestResponse> outgoing(Long userId) {
        return requestRepository.findBySenderIdAndStatusOrderByCreatedAtDesc(userId, FriendRequest.Status.PENDING)
                .stream().map(RequestResponse::from).toList();
    }

    @Transactional
    public RequestResponse send(Long senderId, Long receiverId) {
        validatePair(senderId, receiverId);
        if (!identityClient.exists(receiverId)) {
            throw ApiException.notFound("USER_NOT_FOUND", "Receiver does not exist.");
        }
        if (isBlocked(senderId, receiverId)) {
            throw ApiException.forbidden("USER_BLOCKED", "Friend request is not allowed for a blocked relationship.");
        }
        if (areFriends(senderId, receiverId)) {
            throw ApiException.conflict("ALREADY_FRIENDS", "Users are already friends.");
        }
        pendingPair(senderId, receiverId).ifPresent(existing -> {
            throw ApiException.conflict("REQUEST_ALREADY_PENDING", "A friend request is already pending.");
        });
        FriendRequest saved = requestRepository.save(new FriendRequest(senderId, receiverId));
        notificationClient.friendRequest(receiverId, senderId, saved.getId());
        return RequestResponse.from(saved);
    }

    @Transactional
    public FriendshipResponse accept(Long receiverId, Long requestId) {
        FriendRequest request = pendingOwnedRequest(receiverId, requestId);
        if (isBlocked(request.getSenderId(), request.getReceiverId())) {
            throw ApiException.forbidden("USER_BLOCKED", "Blocked users cannot become friends.");
        }
        request.setStatus(FriendRequest.Status.ACCEPTED);
        requestRepository.save(request);
        Friendship friendship = friendshipRepository.save(new Friendship(request.getSenderId(), request.getReceiverId()));
        notificationClient.friendAccepted(request.getSenderId(), receiverId, friendship.getId());
        return FriendshipResponse.from(friendship);
    }

    @Transactional
    public RequestResponse decline(Long receiverId, Long requestId) {
        FriendRequest request = pendingOwnedRequest(receiverId, requestId);
        request.setStatus(FriendRequest.Status.DECLINED);
        return RequestResponse.from(requestRepository.save(request));
    }

    @Transactional
    public RequestResponse cancel(Long senderId, Long requestId) {
        FriendRequest request = requestRepository.findForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("FRIEND_REQUEST_NOT_FOUND", "Friend request not found."));
        if (!senderId.equals(request.getSenderId())) {
            throw ApiException.forbidden("FRIEND_REQUEST_FORBIDDEN", "Only the sender can cancel this request.");
        }
        if (request.getStatus() == FriendRequest.Status.CANCELLED) return RequestResponse.from(request);
        if (request.getStatus() != FriendRequest.Status.PENDING) {
            throw ApiException.conflict("FRIEND_REQUEST_CLOSED", "Friend request is no longer pending.");
        }
        request.setStatus(FriendRequest.Status.CANCELLED);
        return RequestResponse.from(requestRepository.save(request));
    }

    @Transactional
    public BlockResponse block(Long blockerId, Long blockedId) {
        validatePair(blockerId, blockedId);
        if (!identityClient.exists(blockedId)) {
            throw ApiException.notFound("USER_NOT_FOUND", "User does not exist.");
        }
        List<FriendRequest> pendingRequests = requestRepository.findAllPairByStatusForUpdate(
                blockerId,
                blockedId,
                FriendRequest.Status.PENDING
        );
        Block block = blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .orElseGet(() -> blockRepository.save(new Block(blockerId, blockedId)));
        long a = Math.min(blockerId, blockedId);
        long b = Math.max(blockerId, blockedId);
        friendshipRepository.findByUserIdAAndUserIdB(a, b).ifPresent(friendshipRepository::delete);
        pendingRequests.forEach(request -> {
            request.setStatus(FriendRequest.Status.DECLINED);
            requestRepository.save(request);
        });
        return BlockResponse.from(block);
    }

    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId).ifPresent(blockRepository::delete);
    }

    public List<BlockResponse> blocks(Long blockerId) {
        return blockRepository.findByBlockerIdOrderByCreatedAtDesc(blockerId).stream().map(BlockResponse::from).toList();
    }

    public boolean areFriends(Long a, Long b) {
        if (a == null || b == null || a.equals(b)) return false;
        return friendshipRepository.existsByUserIdAAndUserIdB(Math.min(a, b), Math.max(a, b));
    }

    public boolean isBlocked(Long a, Long b) {
        if (a == null || b == null || a.equals(b)) return false;
        return blockRepository.existsEitherDirection(a, b);
    }

    private java.util.Optional<FriendRequest> pendingPair(Long a, Long b) {
        return requestRepository.findPairByStatus(a, b, FriendRequest.Status.PENDING);
    }

    private FriendRequest pendingOwnedRequest(Long receiverId, Long requestId) {
        FriendRequest request = requestRepository.findForUpdate(requestId)
                .orElseThrow(() -> ApiException.notFound("FRIEND_REQUEST_NOT_FOUND", "Friend request not found."));
        if (!receiverId.equals(request.getReceiverId())) {
            throw ApiException.forbidden("FRIEND_REQUEST_FORBIDDEN", "Only the receiver can handle this request.");
        }
        if (request.getStatus() != FriendRequest.Status.PENDING) {
            throw ApiException.conflict("FRIEND_REQUEST_CLOSED", "Friend request is no longer pending.");
        }
        return request;
    }

    private void validatePair(Long a, Long b) {
        if (a == null || b == null || a.equals(b)) {
            throw ApiException.badRequest("INVALID_USER_PAIR", "Two distinct users are required.");
        }
    }

    public record SearchResult(IdentityClient.UserSummary user, boolean friend, boolean blocked, Long pendingRequestId) {}
    public record RequestResponse(Long id, Long senderId, Long receiverId, String status, Instant createdAt) {
        static RequestResponse from(FriendRequest request) {
            return new RequestResponse(request.getId(), request.getSenderId(), request.getReceiverId(), request.getStatus().name(), request.getCreatedAt());
        }
    }
    public record FriendshipResponse(Long id, Long userIdA, Long userIdB, Instant createdAt) {
        static FriendshipResponse from(Friendship friendship) {
            return new FriendshipResponse(friendship.getId(), friendship.getUserIdA(), friendship.getUserIdB(), friendship.getCreatedAt());
        }
    }
    public record BlockResponse(Long id, Long blockerId, Long blockedId, Instant createdAt) {
        static BlockResponse from(Block block) {
            return new BlockResponse(block.getId(), block.getBlockerId(), block.getBlockedId(), block.getCreatedAt());
        }
    }
}
