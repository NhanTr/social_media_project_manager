class UserModel {
  final int id;
  final String username;
  final String? email;
  final String? phoneNumber;
  final String displayName;
  final String? avatarUrl;
  final String? bio;
  final String status;
  final String? friendInviteCode;

  const UserModel({
    required this.id,
    required this.username,
    this.email,
    this.phoneNumber,
    required this.displayName,
    this.avatarUrl,
    this.bio,
    this.status = 'ACTIVE',
    this.friendInviteCode,
  });

  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      id: json['id'] as int? ?? (json['userId'] as int? ?? 0),
      username: json['username'] as String? ?? 'user_${json['id']}',
      email: json['email'] as String?,
      phoneNumber: json['phoneNumber'] as String?,
      displayName:
          json['displayName'] as String? ??
          json['username'] as String? ??
          'User',
      avatarUrl: json['avatarUrl'] as String?,
      bio: json['bio'] as String?,
      status: json['status'] as String? ?? 'ACTIVE',
      friendInviteCode: json['friendInviteCode'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'username': username,
    'email': email,
    'phoneNumber': phoneNumber,
    'displayName': displayName,
    'avatarUrl': avatarUrl,
    'bio': bio,
    'status': status,
    'friendInviteCode': friendInviteCode,
  };
}

class AuthSession {
  final String accessToken;
  final String refreshToken;
  final String tokenType;
  final int expiresInSeconds;
  final UserModel? user;
  final bool requiresTermsAcceptance;

  const AuthSession({
    required this.accessToken,
    required this.refreshToken,
    this.tokenType = 'Bearer',
    this.expiresInSeconds = 3600,
    this.user,
    this.requiresTermsAcceptance = false,
  });

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    return AuthSession(
      accessToken: json['accessToken'] as String? ?? '',
      refreshToken: json['refreshToken'] as String? ?? '',
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresInSeconds: json['expiresInSeconds'] as int? ?? 3600,
      user: json['user'] != null
          ? UserModel.fromJson(json['user'] as Map<String, dynamic>)
          : null,
      requiresTermsAcceptance:
          json['requiresTermsAcceptance'] as bool? ?? false,
    );
  }
}

class TermsModel {
  final int id;
  final String version;
  final String contentUrl;
  final String? publishedAt;

  const TermsModel({
    required this.id,
    required this.version,
    required this.contentUrl,
    this.publishedAt,
  });

  factory TermsModel.fromJson(Map<String, dynamic> json) {
    return TermsModel(
      id: json['id'] as int? ?? 1,
      version: json['version'] as String? ?? 'v1.0.0',
      contentUrl: json['contentUrl'] as String? ?? '',
      publishedAt: json['publishedAt']?.toString(),
    );
  }
}

class FriendSummary {
  final int id;
  final String username;
  final String displayName;
  final String? avatarUrl;
  final String? bio;

  const FriendSummary({
    required this.id,
    required this.username,
    required this.displayName,
    this.avatarUrl,
    this.bio,
  });

  factory FriendSummary.fromJson(Map<String, dynamic> json) {
    return FriendSummary(
      id: json['id'] as int? ?? 0,
      username: json['username'] as String? ?? '',
      displayName:
          json['displayName'] as String? ?? json['username'] as String? ?? '',
      avatarUrl: json['avatarUrl'] as String?,
      bio: json['bio'] as String?,
    );
  }
}

class FriendRequestModel {
  final int id;
  final int senderId;
  final int receiverId;
  final String status;
  final String? createdAt;

  const FriendRequestModel({
    required this.id,
    required this.senderId,
    required this.receiverId,
    required this.status,
    this.createdAt,
  });

  factory FriendRequestModel.fromJson(Map<String, dynamic> json) {
    return FriendRequestModel(
      id: json['id'] as int? ?? 0,
      senderId: json['senderId'] as int? ?? 0,
      receiverId: json['receiverId'] as int? ?? 0,
      status: json['status'] as String? ?? 'PENDING',
      createdAt: json['createdAt']?.toString(),
    );
  }
}

class BlockModel {
  final int id;
  final int blockerId;
  final int blockedId;
  final String? createdAt;

  const BlockModel({
    required this.id,
    required this.blockerId,
    required this.blockedId,
    this.createdAt,
  });

  factory BlockModel.fromJson(Map<String, dynamic> json) {
    return BlockModel(
      id: json['id'] as int? ?? 0,
      blockerId: json['blockerId'] as int? ?? 0,
      blockedId: json['blockedId'] as int? ?? 0,
      createdAt: json['createdAt']?.toString(),
    );
  }
}

class SearchResultModel {
  final FriendSummary user;
  final bool areFriends;
  final bool isBlocked;
  final int? pendingRequestId;

  const SearchResultModel({
    required this.user,
    required this.areFriends,
    required this.isBlocked,
    this.pendingRequestId,
  });

  factory SearchResultModel.fromJson(Map<String, dynamic> json) {
    return SearchResultModel(
      user: FriendSummary.fromJson(json['user'] as Map<String, dynamic>? ?? {}),
      areFriends: json['friend'] as bool? ?? false,
      isBlocked: json['blocked'] as bool? ?? false,
      pendingRequestId: json['pendingRequestId'] as int?,
    );
  }
}
