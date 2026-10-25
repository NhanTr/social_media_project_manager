class PostModel {
  final int id;
  final int userId;
  final String mediaUrl;
  final String mediaType;
  final String status;
  final String authorName;
  final String? caption;
  final String? thumbnailUrl;
  final DateTime createdAt;

  const PostModel({
    required this.id,
    required this.userId,
    required this.mediaUrl,
    this.mediaType = 'IMAGE',
    this.status = 'ACTIVE',
    this.authorName = '',
    this.caption,
    this.thumbnailUrl,
    required this.createdAt,
  });

  factory PostModel.fromJson(Map<String, dynamic> json) {
    final author = json['author'] as Map<String, dynamic>?;
    return PostModel(
      id: (json['id'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
      mediaUrl: json['mediaUrl'] as String,
      mediaType: json['mediaType'] as String? ?? 'IMAGE',
      status: json['status'] as String? ?? 'ACTIVE',
      authorName:
          author?['displayName'] as String? ??
          author?['username'] as String? ??
          json['authorName'] as String? ??
          '',
      caption: json['caption'] as String?,
      thumbnailUrl: json['thumbnailUrl'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
  Map<String, dynamic> toJson() => {
    'id': id,
    'userId': userId,
    'mediaUrl': mediaUrl,
    'mediaType': mediaType,
    'status': status,
    'authorName': authorName,
    'caption': caption,
    'thumbnailUrl': thumbnailUrl,
    'createdAt': createdAt.toUtc().toIso8601String(),
  };
}

class PostPage {
  final List<PostModel> items;
  final String? nextCursor;
  final bool hasNext;
  const PostPage({required this.items, this.nextCursor, this.hasNext = false});
}

class MediaUploadSession {
  final int uploadId;
  final String presignedUrl;
  const MediaUploadSession({
    required this.uploadId,
    required this.presignedUrl,
  });
}

class UploadedMedia {
  final String mediaUrl;
  final String mediaType;
  final int fileSizeBytes;
  final int? mediaWidth;
  final int? mediaHeight;
  final int? durationSeconds;
  const UploadedMedia({
    required this.mediaUrl,
    required this.mediaType,
    required this.fileSizeBytes,
    this.mediaWidth,
    this.mediaHeight,
    this.durationSeconds,
  });
  factory UploadedMedia.fromJson(Map<String, dynamic> json) => UploadedMedia(
    mediaUrl: json['mediaUrl'] as String,
    mediaType: json['mediaType'] as String,
    fileSizeBytes: (json['fileSizeBytes'] as num).toInt(),
    mediaWidth: (json['mediaWidth'] as num?)?.toInt(),
    mediaHeight: (json['mediaHeight'] as num?)?.toInt(),
    durationSeconds: (json['durationSeconds'] as num?)?.toInt(),
  );
  Map<String, dynamic> toJson() => {
    'mediaUrl': mediaUrl,
    'mediaType': mediaType,
    'fileSizeBytes': fileSizeBytes,
    'mediaWidth': mediaWidth,
    'mediaHeight': mediaHeight,
    'durationSeconds': durationSeconds,
  };
}

class PostApiException implements Exception {
  final int statusCode;
  final String? code;
  final String message;
  const PostApiException({
    required this.statusCode,
    this.code,
    required this.message,
  });
  @override
  String toString() => message;
}
