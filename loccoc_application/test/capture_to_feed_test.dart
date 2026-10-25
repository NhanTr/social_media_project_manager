import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:image/image.dart' as img;
import 'package:loccoc_application/core/models/user_models.dart';
import 'package:loccoc_application/core/network/api_client.dart';
import 'package:loccoc_application/core/network/posts_api.dart';
import 'package:loccoc_application/core/storage/post_local_store.dart';
import 'package:loccoc_application/core/storage/session_storage.dart';
import 'package:loccoc_application/features/auth/auth_controller.dart';
import 'package:loccoc_application/features/composer/composer_adapters.dart';
import 'package:loccoc_application/features/composer/composer_controller.dart';
import 'package:loccoc_application/features/composer/composer_image.dart';
import 'package:loccoc_application/features/composer/composer_screen.dart';
import 'package:loccoc_application/features/feed/feed_cache.dart';
import 'package:loccoc_application/features/feed/feed_controller.dart';
import 'package:loccoc_application/features/feed/media_adapters.dart';
import 'package:loccoc_application/features/shell/app_shell.dart';

class Session extends SessionStorage {
  @override
  Future<String?> getAccessToken() async => null;
}

class Auth extends AuthController {
  Auth(this.client) : super(api: client);
  final ApiClient client;
  UserModel? user = const UserModel(
    id: 1,
    username: 'tester',
    displayName: 'Tester',
  );
  AuthStatus currentStatus = AuthStatus.authenticated;
  @override
  ApiClient get apiClient => client;
  @override
  UserModel? get currentUser => user;
  @override
  AuthStatus get status => currentStatus;

  void switchSession(UserModel? nextUser, AuthStatus nextStatus) {
    user = nextUser;
    currentStatus = nextStatus;
    notifyListeners();
  }
}

class MemoryStore extends PostLocalStore {
  MemoryStore({required super.accountId});
  final Map<String, Map<String, dynamic>> values = {};
  @override
  Future<Map<String, dynamic>?> read(String key) async => values[key] == null
      ? null
      : jsonDecode(jsonEncode(values[key])) as Map<String, dynamic>;
  @override
  Future<void> write(String key, Map<String, dynamic> value) async {
    values[key] = jsonDecode(jsonEncode(value)) as Map<String, dynamic>;
  }

  @override
  Future<void> remove(String key) async {
    values.remove(key);
  }

  @override
  Future<void> clear() async {
    values.clear();
  }
}

class Draft implements ComposerDraftStore {
  Map<String, dynamic>? data;
  @override
  Future<Map<String, dynamic>?> read() async => data;
  @override
  Future<void> write(Map<String, dynamic> draft) async {
    data = jsonDecode(jsonEncode(draft)) as Map<String, dynamic>;
  }

  @override
  Future<void> remove() async {
    data = null;
  }
}

class Picker implements ComposerPhotoPicker {
  @override
  Future<Uint8List?> recover() async => null;
  @override
  Future<Uint8List?> pick({required bool camera}) async =>
      Uint8List.fromList(img.encodeJpg(img.Image(width: 16, height: 16)));
}

class FixtureProcessor implements PhotoProcessor {
  @override
  Future<PreparedPhoto> process(
    Uint8List source,
    PhotoSettings settings,
  ) async => PreparedPhoto(source, 16, 16);
}

class Upload implements PhotoUploadTransport {
  @override
  Future<void> put(
    String url,
    Uint8List bytes,
    UploadAbort abort,
    void Function(int, int) progress,
  ) async {
    progress(bytes.length, bytes.length);
  }
}

class Download implements MediaDownloader {
  @override
  Future<Uint8List> download(
    String url, {
    required int maxBytes,
    required Duration timeout,
  }) async =>
      Uint8List.fromList(img.encodeJpg(img.Image(width: 16, height: 16)));
}

void main() {
  testWidgets(
    'capture composer publishes via HTTP contract then returns and refreshes friends feed',
    (tester) async {
      var feeds = 0;
      var creates = 0;
      final client = ApiClient(
        storage: Session(),
        client: MockClient((r) async {
          if (r.url.path == '/api/v1/feed') {
            feeds++;
            return http.Response(
              '{"items":[],"hasNext":false,"nextCursor":null}',
              200,
            );
          }
          if (r.url.path == '/api/v1/media/uploads') {
            return http.Response(
              '{"uploadId":3,"presignedUrl":"https://upload"}',
              201,
            );
          }
          if (r.url.path.endsWith('/finalize')) {
            return http.Response(
              '{"mediaType":"IMAGE","mediaUrl":"https://media/a.jpg","fileSizeBytes":630,"mediaWidth":16,"mediaHeight":16}',
              200,
            );
          }
          if (r.url.path == '/api/v1/posts') {
            creates++;
            final json = jsonDecode(r.body) as Map<String, dynamic>;
            expect(
              json['clientRequestId'],
              matches(RegExp(r'^[a-f0-9-]{36}$')),
            );
            expect(json['uploadId'], 3);
            return http.Response(
              '{"id":9,"userId":1,"mediaUrl":"https://media/a.jpg","createdAt":"2026-10-25T12:00:00Z"}',
              201,
            );
          }
          return http.Response('[]', 200);
        }),
      );
      final api = PostsApi(client);
      final draft = Draft();
      final composer = ComposerController(
        api: api,
        accountId: 1,
        store: draft,
        picker: Picker(),
        processor: FixtureProcessor(),
        upload: Upload(),
      );
      final store = MemoryStore(accountId: 1);
      final feed = FeedController(
        api: api,
        accountId: 1,
        cache: FeedCache(store: store),
        downloader: Download(),
      );
      final auth = Auth(client);
      await composer.initialize();
      await composer.pick(camera: false);
      await composer.setCaption('A new moment');
      await tester.pumpWidget(
        MaterialApp(
          home: AppShell(
            authController: auth,
            postsApi: api,
            feedController: feed,
            composerBuilder: (api, id) =>
                ComposerScreen(api: api, accountId: id, controller: composer),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final before = feeds;
      await tester.tap(find.byTooltip('Chia sẻ ảnh'));
      await tester.pumpAndSettle();
      final composerScroll = find.descendant(
        of: find.byType(ComposerScreen),
        matching: find.byType(Scrollable),
      );
      await tester.scrollUntilVisible(
        find.text('Đăng ảnh'),
        200,
        scrollable: composerScroll.first,
      );
      await tester.ensureVisible(find.text('Đăng ảnh'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Đăng ảnh'));
      await tester.pumpAndSettle();
      expect(creates, 1);
      expect(feeds, greaterThan(before));
      expect(find.text('Đã đăng ảnh thành công.'), findsOneWidget);
      expect(draft.data, isNull);

      auth.switchSession(null, AuthStatus.unauthenticated);
      await tester.pumpAndSettle();
      expect(store.values, isEmpty);

      await tester.pumpWidget(const SizedBox.shrink());
      composer.dispose();
      feed.dispose();
    },
  );

  testWidgets(
    'friend block invalidates feed cache and account switch recreates isolated shell',
    (tester) async {
      var blocked = false;
      final client = ApiClient(
        storage: Session(),
        client: MockClient((r) async {
          if (r.url.path == '/api/v1/feed') {
            return http.Response(
              jsonEncode({
                'items': [
                  {
                    'id': 7,
                    'userId': 2,
                    'authorName': 'Mai',
                    'mediaUrl': 'https://media/7.jpg',
                    'mediaType': 'IMAGE',
                    'caption': 'Mai post',
                    'status': 'ACTIVE',
                    'createdAt': '2026-10-25T10:00:00Z',
                  },
                ],
                'hasNext': false,
                'nextCursor': null,
              }),
              200,
            );
          }
          if (r.url.path == '/api/v1/friends') {
            return http.Response(
              jsonEncode(
                blocked
                    ? []
                    : [
                        {'id': 2, 'username': 'mai', 'displayName': 'Mai'},
                      ],
              ),
              200,
            );
          }
          if (r.url.path == '/api/v1/blocks' && r.method == 'POST') {
            blocked = true;
            return http.Response('{}', 200);
          }
          return http.Response('[]', 200);
        }),
      );
      final api = PostsApi(client);
      final store1 = MemoryStore(accountId: 1);
      final feed1 = FeedController(
        api: api,
        accountId: 1,
        cache: FeedCache(store: store1),
        downloader: Download(),
      );
      final auth = Auth(client);
      await tester.pumpWidget(
        ListenableBuilder(
          listenable: auth,
          builder: (context, _) => MaterialApp(
            home: AppShell(
              key: ValueKey(auth.currentUser?.id),
              authController: auth,
              postsApi: api,
              feedController: auth.currentUser?.id == 1 ? feed1 : null,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Mai post'), findsOneWidget);
      expect(store1.values, isNotEmpty);

      await tester.tap(find.text('Bạn bè'));
      await tester.pumpAndSettle();
      await tester.tap(find.byIcon(Icons.more_vert));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Chặn người này'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Chặn người dùng'));
      await tester.pumpAndSettle();

      expect(feed1.items, isEmpty);

      auth.switchSession(
        const UserModel(id: 2, username: 'other', displayName: 'Other'),
        AuthStatus.authenticated,
      );
      await tester.pumpAndSettle();
      expect(store1.values, isEmpty);

      await tester.pumpWidget(const SizedBox.shrink());
      feed1.dispose();
    },
  );
}
