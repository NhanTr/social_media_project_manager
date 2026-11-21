import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:loccoc_application/core/network/api_client.dart';
import 'package:loccoc_application/core/network/posts_api.dart';
import 'package:loccoc_application/core/models/post_models.dart';
import 'package:loccoc_application/core/storage/session_storage.dart';

class NoSession extends SessionStorage {
  @override
  Future<String?> getAccessToken() async => null;
}

void main() {
  test(
    'friend feed passes opaque cursor unchanged and parses author and page',
    () async {
      final api = PostsApi(
        ApiClient(
          storage: NoSession(),
          client: MockClient((r) async {
            expect(r.url.path, '/api/v1/feed/by-friend/7');
            expect(r.url.queryParameters, {'cursor': 'a+/==', 'limit': '20'});
            return http.Response(
              jsonEncode({
                'items': [
                  {
                    'id': 12,
                    'userId': 7,
                    'author': {'displayName': 'Mai'},
                    'mediaUrl': 'https://media/image.jpg',
                    'mediaType': 'IMAGE',
                    'status': 'ACTIVE',
                    'createdAt': '2026-10-25T12:00:00Z',
                  },
                ],
                'hasNext': true,
                'nextCursor': 'opaque!',
              }),
              200,
            );
          }),
        ),
      );
      final page = await api.feed(friendId: 7, cursor: 'a+/==');
      expect(page.items.single.authorName, 'Mai');
      expect(page.items.single.id, 12);
      expect(page.nextCursor, 'opaque!');
      expect(page.hasNext, isTrue);
      expect(PostModel.fromJson(page.items.single.toJson()).userId, 7);
    },
  );
  test(
    'publication transmits stable request id and finalized upload metadata',
    () async {
      final api = PostsApi(
        ApiClient(
          storage: NoSession(),
          client: MockClient((r) async {
            expect(r.url.path, '/api/v1/posts');
            final body = jsonDecode(r.body) as Map<String, dynamic>;
            expect(
              body['clientRequestId'],
              'f8aa7bf5-2f4b-4ae4-8779-9bbda56cb36f',
            );
            expect(body['uploadId'], 3);
            expect(body['fileSizeBytes'], 25);
            expect(body['caption'], 'hello');
            expect(body['visibility'], 'FRIENDS_ONLY');
            return http.Response(
              '{"id":9,"userId":1,"mediaUrl":"https://media/a.jpg","createdAt":"2026-10-25T12:00:00Z"}',
              201,
            );
          }),
        ),
      );
      final post = await api.publish(
        clientRequestId: 'f8aa7bf5-2f4b-4ae4-8779-9bbda56cb36f',
        uploadId: 3,
        media: const UploadedMedia(
          mediaUrl: 'https://media/a.jpg',
          mediaType: 'IMAGE',
          fileSizeBytes: 25,
        ),
        caption: 'hello',
        filterApplied: 'original',
      );
      expect(post.id, 9);
    },
  );
  test(
    'upload contract creates JPEG session and maps forbidden to typed error',
    () async {
      var calls = 0;
      final api = PostsApi(
        ApiClient(
          storage: NoSession(),
          client: MockClient((r) async {
            if (calls++ == 0) {
              expect(jsonDecode(r.body), {
                'contentType': 'image/jpeg',
                'declaredSizeBytes': 1024,
              });
              return http.Response(
                '{"uploadId":3,"presignedUrl":"https://storage/put"}',
                201,
              );
            }
            return http.Response(
              '{"code":"POST_FORBIDDEN","detail":"No access"}',
              403,
            );
          }),
        ),
      );
      expect((await api.createUpload(1024)).uploadId, 3);
      await expectLater(
        api.detail(9),
        throwsA(
          isA<PostApiException>()
              .having((e) => e.statusCode, 'status', 403)
              .having((e) => e.code, 'code', 'POST_FORBIDDEN'),
        ),
      );
    },
  );
  test(
    'malformed success is rejected instead of silently empty feed',
    () async {
      final api = PostsApi(
        ApiClient(
          storage: NoSession(),
          client: MockClient((r) async => http.Response('{}', 200)),
        ),
      );
      await expectLater(api.feed(), throwsA(isA<PostApiException>()));
    },
  );
}
