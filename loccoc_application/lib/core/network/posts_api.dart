import '../models/post_models.dart';
import '../models/user_models.dart';
import 'api_client.dart';

class PostsApi {
  final ApiClient api;
  PostsApi(this.api);

  Future<dynamic> _data(Future<ApiResponse<dynamic>> request) async {
    final response = await request;
    if (!response.success) {
      throw PostApiException(
        statusCode: response.statusCode,
        code: response.errorCode,
        message: response.errorMessage ?? 'Không thể tải dữ liệu.',
      );
    }
    return response.data;
  }

  Never _invalid() => throw const PostApiException(
    statusCode: 502,
    code: 'INVALID_RESPONSE',
    message: 'Máy chủ trả về dữ liệu không hợp lệ. Vui lòng thử lại.',
  );

  Future<T> _parse<T>(Future<dynamic> data, T Function(dynamic) parse) async {
    final value = await data;
    try {
      return parse(value);
    } on FormatException {
      _invalid();
    } on TypeError {
      _invalid();
    }
  }

  Future<PostPage> feed({int? friendId, String? cursor, int limit = 20}) =>
      _parse(
        _data(
          api.get(
            friendId == null
                ? '/api/v1/feed'
                : '/api/v1/feed/by-friend/$friendId',
            queryParams: {'limit': '$limit', 'cursor': ?cursor},
          ),
        ),
        (data) {
          final json = data as Map<String, dynamic>;
          final items = (json['items'] as List)
              .map((v) => PostModel.fromJson(v as Map<String, dynamic>))
              .toList();
          final hasNext = json['hasNext'] as bool;
          final next = json['nextCursor'] as String?;
          if (hasNext && (next == null || next.isEmpty)) _invalid();
          return PostPage(items: items, nextCursor: next, hasNext: hasNext);
        },
      );

  Future<PostModel> detail(int id) => _parse(
    _data(api.get('/api/v1/posts/$id')),
    (data) => PostModel.fromJson(data as Map<String, dynamic>),
  );

  Future<MediaUploadSession> createUpload(int bytes) => _parse(
    _data(
      api.post(
        '/api/v1/media/uploads',
        body: {'contentType': 'image/jpeg', 'declaredSizeBytes': bytes},
      ),
    ),
    (data) {
      final json = data as Map<String, dynamic>;
      return MediaUploadSession(
        uploadId: (json['uploadId'] as num).toInt(),
        presignedUrl: json['presignedUrl'] as String,
      );
    },
  );

  Future<UploadedMedia> finalizeUpload(int uploadId) => _parse(
    _data(api.post('/api/v1/media/uploads/$uploadId/finalize')),
    (data) => UploadedMedia.fromJson(data as Map<String, dynamic>),
  );

  Future<PostModel> publish({
    required String clientRequestId,
    required int uploadId,
    required UploadedMedia media,
    required String caption,
    required String filterApplied,
  }) => _parse(
    _data(
      api.post(
        '/api/v1/posts',
        body: {
          ...media.toJson(),
          'clientRequestId': clientRequestId,
          'uploadId': uploadId,
          'caption': caption,
          'filterApplied': filterApplied,
          'aiGenerated': false,
          'visibility': 'FRIENDS_ONLY',
        },
      ),
    ),
    (data) => PostModel.fromJson(data as Map<String, dynamic>),
  );

  Future<List<FriendSummary>> friends() => _parse(
    _data(api.get('/api/v1/friends')),
    (data) => (data as List)
        .map((v) => FriendSummary.fromJson(v as Map<String, dynamic>))
        .toList(),
  );
}
