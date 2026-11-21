import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:image/image.dart' as img;
import 'package:integration_test/integration_test.dart';
import 'package:loccoc_application/core/models/post_models.dart';
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
import 'package:loccoc_application/features/feed/media_detail_controller.dart';
import 'package:loccoc_application/features/feed/media_detail_screen.dart';
import 'package:loccoc_application/features/friends/friends_controller.dart';
import 'package:loccoc_application/features/shell/app_shell.dart';

class _IsolatedSessionStorage extends SessionStorage {
  String? _accessToken;
  String? _refreshToken;
  int? _userId;
  String? _username;
  String? _displayName;
  bool _requiresTerms = false;

  @override
  Future<void> saveSession({
    required String accessToken,
    required String refreshToken,
    required int userId,
    required String username,
    required String displayName,
    required bool requiresTerms,
  }) async {
    _accessToken = accessToken;
    _refreshToken = refreshToken;
    _userId = userId;
    _username = username;
    _displayName = displayName;
    _requiresTerms = requiresTerms;
  }

  @override
  Future<void> updateTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    _accessToken = accessToken;
    _refreshToken = refreshToken;
  }

  @override
  Future<void> setTermsAccepted() async {
    _requiresTerms = false;
  }

  @override
  Future<String?> getAccessToken() async => _accessToken;

  @override
  Future<String?> getRefreshToken() async => _refreshToken;

  @override
  Future<int?> getUserId() async => _userId;

  @override
  Future<String?> getUsername() async => _username;

  @override
  Future<String?> getDisplayName() async => _displayName;

  @override
  Future<bool> getRequiresTerms() async => _requiresTerms;

  @override
  Future<void> clear() async {
    _accessToken = null;
    _refreshToken = null;
    _userId = null;
    _username = null;
    _displayName = null;
    _requiresTerms = false;
  }
}

class _SyntheticLargePhotoPicker implements ComposerPhotoPicker {
  _SyntheticLargePhotoPicker(this.bytes);
  final Uint8List bytes;

  @override
  Future<Uint8List?> recover() async => null;

  @override
  Future<Uint8List?> pick({required bool camera}) async => bytes;
}

class _CachingRealPhotoProcessor implements PhotoProcessor {
  final LocalPhotoProcessor _delegate = const LocalPhotoProcessor();
  final Map<String, PreparedPhoto> _cache = {};

  @override
  Future<PreparedPhoto> process(
    Uint8List source,
    PhotoSettings settings,
  ) async {
    final key =
        '${source.length}:${settings.square}:${settings.cropX}:${settings.cropY}:${settings.filter.name}';
    final cached = _cache[key];
    if (cached != null) return cached;
    final computed = await _delegate.process(source, settings);
    _cache[key] = computed;
    return computed;
  }
}

class _PausableUploadTransport implements PhotoUploadTransport {
  _PausableUploadTransport(this._delegate);
  final PhotoUploadTransport _delegate;
  Completer<void>? pauseGate;

  @override
  Future<void> put(
    String url,
    Uint8List bytes,
    UploadAbort abort,
    void Function(int sent, int total) progress,
  ) async {
    final gate = pauseGate;
    if (gate != null) {
      progress(bytes.length ~/ 3, bytes.length);
      await gate.future;
      if (abort.cancelled) return;
    }
    await _delegate.put(url, bytes, abort, progress);
  }
}

class _SingleItemFeedApi extends PostsApi {
  _SingleItemFeedApi(super.api);

  @override
  Future<PostPage> feed({int? friendId, String? cursor, int limit = 1}) =>
      super.feed(friendId: friendId, cursor: cursor, limit: 1);
}

/// Drops only the response after a real successful server commit. The replay
/// must retain the exact upload, media and publication UUID from that request.
class _LoseFirstPublishResponseApi extends PostsApi {
  _LoseFirstPublishResponseApi(super.api);
  PostModel? committed;

  @override
  Future<PostModel> publish({
    required String clientRequestId,
    required int uploadId,
    required UploadedMedia media,
    required String caption,
    required String filterApplied,
  }) async {
    final result = await super.publish(
      clientRequestId: clientRequestId,
      uploadId: uploadId,
      media: media,
      caption: caption,
      filterApplied: filterApplied,
    );
    if (committed == null) {
      committed = result;
      throw const PostApiException(
        statusCode: 503,
        message: 'Test: response lost after server commit',
      );
    }
    return result;
  }
}

Future<void> _pumpUntil(
  WidgetTester tester,
  bool Function() condition, {
  Duration timeout = const Duration(seconds: 25),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (!condition()) {
    if (DateTime.now().isAfter(deadline)) {
      fail('Timed out waiting for condition after $timeout');
    }
    await tester.pump(const Duration(milliseconds: 100));
  }
  await tester.pump(const Duration(milliseconds: 100));
}

Uint8List _generateLargeJpeg({
  required int width,
  required int height,
  required int r,
  required int g,
  required int b,
}) {
  final canvas = img.Image(width: width, height: height);
  img.fill(canvas, color: img.ColorRgb8(r, g, b));
  for (var y = 0; y < height; y += 64) {
    for (var x = 0; x < width; x += 64) {
      canvas.setPixelRgb(x, y, (r + x) % 255, (g + y) % 255, b);
    }
  }
  return Uint8List.fromList(img.encodeJpg(canvas, quality: 92));
}

Future<AuthController> _authenticateLiveAccount({
  required String phone,
  required String displayName,
}) async {
  final storage = _IsolatedSessionStorage();
  final api = ApiClient(storage: storage);
  final auth = AuthController(api: api, storage: storage);
  final otpSent = await auth.requestPhoneOtp(phone);
  expect(
    otpSent,
    isTrue,
    reason: 'send-otp failed for $phone: ${auth.errorMessage}',
  );
  expect(auth.lastOtpMock, isNotNull);
  final verified = await auth.verifyPhoneOtp(phone, auth.lastOtpMock!);
  expect(
    verified,
    isTrue,
    reason: 'verify-otp failed for $phone: ${auth.errorMessage}',
  );
  if (auth.status == AuthStatus.requiresTerms) {
    final terms = await auth.getLatestTerms();
    expect(terms, isNotNull);
    final accepted = await auth.acceptTerms(terms!.id);
    expect(
      accepted,
      isTrue,
      reason: 'acceptTerms failed for $phone: ${auth.errorMessage}',
    );
  }
  expect(auth.status, AuthStatus.authenticated);
  final updated = await auth.updateProfile(displayName: displayName);
  expect(updated, isTrue);
  expect(auth.currentUser?.displayName, displayName);
  return auth;
}

Future<void> _makeFriends(
  AuthController sender,
  AuthController receiver,
) async {
  final senderFriends = FriendsController(api: sender.apiClient);
  final receiverFriends = FriendsController(api: receiver.apiClient);
  final sent = await senderFriends.sendFriendRequest(receiver.currentUser!.id);
  expect(sent, isTrue, reason: senderFriends.errorMessage);
  await receiverFriends.loadIncomingRequests();
  final req = receiverFriends.incomingRequests.firstWhere(
    (r) => r.senderId == sender.currentUser!.id,
  );
  final accepted = await receiverFriends.acceptFriendRequest(req.id);
  expect(accepted, isTrue, reason: receiverFriends.errorMessage);
  senderFriends.dispose();
  receiverFriends.dispose();
}

Future<PostModel> _publishDirectPost({
  required PostsApi api,
  required String caption,
  required int r,
  required int g,
  required int b,
}) async {
  final bytes = _generateLargeJpeg(width: 240, height: 240, r: r, g: g, b: b);
  final session = await api.createUpload(bytes.length);
  await DioPhotoUploadTransport().put(
    session.presignedUrl,
    bytes,
    UploadAbort(),
    (_, _) {},
  );
  final media = await api.finalizeUpload(session.uploadId);
  return api.publish(
    uploadId: session.uploadId,
    media: media,
    caption: caption,
    filterApplied: 'original',
    clientRequestId:
        '00000000-1111-4222-8333-${DateTime.now().microsecondsSinceEpoch.toString().padLeft(12, '0').substring(0, 12)}',
  );
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  const restartPhase = String.fromEnvironment(
    'PROCESS_RESTART_PHASE',
    defaultValue: '',
  );
  const restartStoreDir = String.fromEnvironment(
    'RESTART_STORE_DIR',
    defaultValue: '',
  );

  if (restartPhase == 'write') {
    testWidgets(
      'process-restart phase 1: writes composer draft and feed cache to real ApplicationSupportDirectory',
      (tester) async {
        const restartAccountId = 88001;
        final appSupportStore = PostLocalStore(accountId: restartAccountId);
        await appSupportStore.clear();
        final store = PostLocalStore(
          accountId: restartAccountId,
          directory: restartStoreDir.isEmpty ? null : restartStoreDir,
        );
        await store.clear();
        final draftStore = LocalComposerDraftStore(
          restartAccountId,
          store: store,
        );
        final rawBytes = _generateLargeJpeg(
          width: 96,
          height: 96,
          r: 200,
          g: 110,
          b: 45,
        );
        final draftPayload = {
          'version': 1,
          'accountId': restartAccountId,
          'clientRequestId': '77777777-8888-4999-8aaa-bbbbbbbbbbbb',
          'caption': 'Draft sống sót sau khi kill tiến trình OS',
          'settings': const PhotoSettings(
            square: true,
            cropX: 0.25,
            cropY: 0.75,
            filter: PhotoFilter.warm,
          ).toJson(),
          'stage': 'editing',
          'source': base64Encode(rawBytes),
          'prepared': {
            'bytes': base64Encode(rawBytes),
            'width': 96,
            'height': 96,
          },
        };
        await draftStore.write(draftPayload);
        await LocalComposerDraftStore(
          restartAccountId,
          store: appSupportStore,
        ).write(draftPayload);

        final cache = FeedCache(store: store);
        await cache.write(
          null,
          FeedSnapshot(
            posts: [
              PostModel(
                id: 7701,
                userId: 88002,
                authorName: 'Bạn Restart',
                mediaUrl: 'https://example.invalid/restart.jpg',
                mediaType: 'IMAGE',
                caption: 'Bài viết cache qua khởi động lại OS',
                status: 'ACTIVE',
                createdAt: DateTime.utc(2026, 10, 27, 12),
              ),
            ],
            savedAt: DateTime.utc(2026, 10, 27, 12, 5),
            previews: {7701: rawBytes},
          ),
        );

        final verifyDraft = await draftStore.read();
        expect(verifyDraft, isNotNull);
        if (const bool.fromEnvironment('HOLD_FOR_PROCESS_KILL')) {
          debugPrint('S2_RESTART_READY');
          await Future<void>.delayed(const Duration(minutes: 3));
          fail('Host did not terminate the process after the restart marker');
        }
        expect(
          verifyDraft!['clientRequestId'],
          '77777777-8888-4999-8aaa-bbbbbbbbbbbb',
        );
      },
    );
    return;
  }

  if (restartPhase == 'verify') {
    testWidgets(
      'process-restart phase 2: restores composer draft and feed cache after real OS process kill',
      (tester) async {
        const restartAccountId = 88001;
        final store = PostLocalStore(
          accountId: restartAccountId,
          directory: restartStoreDir.isEmpty ? null : restartStoreDir,
        );
        final draftStore = LocalComposerDraftStore(
          restartAccountId,
          store: store,
        );
        final offlineApi = PostsApi(
          ApiClient(
            storage: _IsolatedSessionStorage(),
            client: MockClient((_) async => http.Response('offline', 503)),
          ),
        );

        final composer = ComposerController(
          api: offlineApi,
          accountId: restartAccountId,
          store: draftStore,
        );
        await composer.initialize();
        expect(composer.caption, 'Draft sống sót sau khi kill tiến trình OS');
        expect(composer.settings.square, isTrue);
        expect(composer.settings.filter, PhotoFilter.warm);
        expect(composer.settings.cropX, closeTo(0.25, 0.001));
        expect(composer.settings.cropY, closeTo(0.75, 0.001));
        expect(composer.prepared, isNotNull);
        expect(
          composer.clientRequestId,
          '77777777-8888-4999-8aaa-bbbbbbbbbbbb',
        );

        final cache = FeedCache(store: store);
        final feed = FeedController(
          api: offlineApi,
          accountId: restartAccountId,
          cache: cache,
        );
        await feed.refresh();
        expect(feed.offline, isTrue);
        expect(feed.items.map((p) => p.id).toList(), [7701]);
        expect(feed.previewFor(7701), isNotEmpty);

        composer.dispose();
        feed.dispose();
        await store.clear();
      },
    );
    return;
  }

  testWidgets(
    'live-backend multi-account S2-02 composer upload and S2-03 feed/detail/cache/isolation E2E',
    (tester) async {
      final seed = (DateTime.now().millisecondsSinceEpoch % 8000000) + 1000000;
      final authA = await _authenticateLiveAccount(
        phone: '+8491$seed',
        displayName: 'Author An',
      );
      final authB = await _authenticateLiveAccount(
        phone: '+8492$seed',
        displayName: 'Viewer Binh',
      );
      final authC = await _authenticateLiveAccount(
        phone: '+8493$seed',
        displayName: 'Author Chi',
      );
      final authD = await _authenticateLiveAccount(
        phone: '+8494$seed',
        displayName: 'Stranger Dung',
      );

      final userA = authA.currentUser!;
      final userB = authB.currentUser!;
      final userC = authC.currentUser!;

      await _makeFriends(authA, authB);
      await _makeFriends(authC, authB);

      final apiA = PostsApi(authA.apiClient);
      final apiB = PostsApi(authB.apiClient);
      final apiC = PostsApi(authC.apiClient);
      final apiD = PostsApi(authD.apiClient);
      final composerApi = _LoseFirstPublishResponseApi(authA.apiClient);

      // Stranger D (non-friend of B) and Friend C publish posts.
      final postD1 = await _publishDirectPost(
        api: apiD,
        caption: 'Ảnh người lạ D1',
        r: 90,
        g: 90,
        b: 90,
      );
      final postC1 = await _publishDirectPost(
        api: apiC,
        caption: 'Khoảnh khắc Chi C1',
        r: 40,
        g: 160,
        b: 95,
      );

      // --- S2-02: Account A Composer E2E ---
      final storeA = PostLocalStore(accountId: userA.id);
      await storeA.clear();
      final draftStoreA = LocalComposerDraftStore(userA.id);
      final oversizedBytes = _generateLargeJpeg(
        width: 3200,
        height: 2400,
        r: 210,
        g: 95,
        b: 55,
      );
      final pausableUpload = _PausableUploadTransport(
        DioPhotoUploadTransport(),
      );
      final realProcessor = _CachingRealPhotoProcessor();

      var composerA = ComposerController(
        api: composerApi,
        accountId: userA.id,
        store: draftStoreA,
        picker: _SyntheticLargePhotoPicker(oversizedBytes),
        processor: realProcessor,
        upload: pausableUpload,
      );
      await composerA.initialize();
      await composerA.pick(camera: false);
      await composerA.setSettings(
        const PhotoSettings(
          square: true,
          cropX: 0.25,
          cropY: 0.75,
          filter: PhotoFilter.warm,
        ),
      );
      final previewPhoto = await composerA.preview();
      expect(previewPhoto, isNotNull);
      expect(
        max(previewPhoto!.width, previewPhoto.height),
        lessThanOrEqualTo(2048),
      );
      expect(previewPhoto.width, previewPhoto.height);
      expect(previewPhoto.bytes.length, lessThan(5 * 1024 * 1024));

      // Caption validation > 2000 UTF-16 chars rejected on publish.
      await composerA.setCaption('x' * 2001);
      expect(await composerA.publish(), isNull);
      expect(composerA.error, 'Chú thích tối đa 2000 ký tự.');
      await composerA.setCaption('Khoảnh khắc An A1 ấm áp');
      expect(composerA.error, isNull);

      final savedClientRequestId = composerA.clientRequestId;
      expect(savedClientRequestId, matches(RegExp(r'^[a-f0-9-]{36}$')));

      // Verify real on-disk draft restoration across controller disposal/re-creation.
      composerA.dispose();
      composerA = ComposerController(
        api: composerApi,
        accountId: userA.id,
        store: draftStoreA,
        picker: _SyntheticLargePhotoPicker(oversizedBytes),
        processor: realProcessor,
        upload: pausableUpload,
      );
      await composerA.initialize();
      expect(composerA.caption, 'Khoảnh khắc An A1 ấm áp');
      expect(composerA.settings.square, isTrue);
      expect(composerA.settings.filter, PhotoFilter.warm);
      expect(composerA.settings.cropX, closeTo(0.25, 0.001));
      expect(composerA.settings.cropY, closeTo(0.75, 0.001));
      expect(composerA.clientRequestId, savedClientRequestId);
      expect(composerA.source, isNotNull);

      // Verify upload progress & cancel preserves draft and clientRequestId.
      final gate = Completer<void>();
      pausableUpload.pauseGate = gate;
      final cancelledPublish = composerA.publish();
      while (composerA.stage != ComposerStage.uploading) {
        await Future<void>.delayed(const Duration(milliseconds: 50));
      }
      await Future<void>.delayed(const Duration(milliseconds: 50));
      expect(composerA.progress, greaterThan(0));
      await composerA.cancel();
      gate.complete();
      final cancelledResult = await cancelledPublish;
      expect(cancelledResult, isNull);
      expect(composerA.stage, ComposerStage.cancelled);
      expect(composerA.clientRequestId, savedClientRequestId);
      expect(composerA.prepared, isNotNull);
      expect(await draftStoreA.read(), isNotNull);
      pausableUpload.pauseGate = null;

      // Commit through the production API, lose its response, then restore the
      // durable uncertain request before retrying from the composer UI.
      expect(await composerA.publish(), isNull);
      expect(composerA.uncertain, isTrue);
      final firstServerPost = composerApi.committed!;
      composerA.dispose();
      composerA = ComposerController(
        api: composerApi,
        accountId: userA.id,
        store: draftStoreA,
        picker: _SyntheticLargePhotoPicker(oversizedBytes),
        processor: realProcessor,
        upload: pausableUpload,
      );
      await composerA.initialize();
      expect(composerA.uncertain, isTrue);

      // Now mount AppShell as Account A, open ComposerScreen, and tap 'Đăng ảnh'.
      // The exact same request must return firstServerPost without a duplicate.
      final feedA = FeedController(
        api: apiA,
        accountId: userA.id,
        cache: FeedCache(store: storeA),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: AppShell(
            authController: authA,
            postsApi: apiA,
            feedController: feedA,
            composerBuilder: (api, id) =>
                ComposerScreen(api: api, accountId: id, controller: composerA),
          ),
        ),
      );
      await _pumpUntil(tester, () => !feedA.loading);

      await tester.tap(find.byTooltip('Chia sẻ ảnh'));
      await _pumpUntil(
        tester,
        () => find.byType(ComposerScreen).evaluate().isNotEmpty,
      );
      final composerScroll = find.descendant(
        of: find.byType(ComposerScreen),
        matching: find.byType(Scrollable),
      );
      await tester.scrollUntilVisible(
        find.text('Thử lại để xác nhận'),
        200,
        scrollable: composerScroll.first,
      );
      await tester.ensureVisible(find.text('Thử lại để xác nhận'));
      await tester.pump(const Duration(milliseconds: 100));
      await tester.tap(find.text('Thử lại để xác nhận'));
      await _pumpUntil(
        tester,
        () =>
            !composerA.busy &&
            find.text('Đã đăng ảnh thành công.').evaluate().isNotEmpty,
      );

      expect(find.text('Đã đăng ảnh thành công.'), findsOneWidget);
      expect(await draftStoreA.read(), isNull);

      // Account A also publishes a second post A2 so A has 2 posts (A1, A2).
      await Future<void>.delayed(const Duration(milliseconds: 50));
      final postA2 = await _publishDirectPost(
        api: apiA,
        caption: 'Khoảnh khắc An A2',
        r: 35,
        g: 110,
        b: 205,
      );

      // --- S2-03: Account B Friends-Only Feed, Pagination, Filter, Detail/Save, Offline Cache, Delete/Block Invalidation ---
      final storeB = PostLocalStore(accountId: userB.id);
      await storeB.clear();
      final cacheB = FeedCache(store: storeB);

      // 1. Cursor pagination with limit=1 against live Gateway/PostService:
      final pagedFeedB = FeedController(
        api: _SingleItemFeedApi(authB.apiClient),
        accountId: userB.id,
        cache: cacheB,
      );
      await pagedFeedB.refresh();
      expect(pagedFeedB.items.length, 1);
      expect(pagedFeedB.hasNext, isTrue);
      await pagedFeedB.loadMore();
      expect(pagedFeedB.items.length, 2);
      expect(pagedFeedB.hasNext, isTrue);
      await pagedFeedB.loadMore();
      expect(pagedFeedB.items.length, 3);
      expect(pagedFeedB.hasNext, isFalse);

      final pagedIds = pagedFeedB.items.map((p) => p.id).toList();
      expect(pagedIds.toSet().length, 3);
      expect(pagedIds, containsAll([postA2.id, firstServerPost.id, postC1.id]));
      expect(pagedIds, isNot(contains(postD1.id)));
      pagedFeedB.dispose();

      // 2. Full FeedController in AppShell for Account B:
      final feedB = FeedController(
        api: apiB,
        accountId: userB.id,
        cache: cacheB,
      );
      await tester.pumpWidget(
        MaterialApp(
          home: AppShell(
            key: ValueKey(userB.id),
            authController: authB,
            postsApi: apiB,
            feedController: feedB,
          ),
        ),
      );
      await _pumpUntil(tester, () => !feedB.loading && feedB.items.length == 3);

      expect(feedB.items.map((p) => p.id).toList(), [
        postA2.id,
        firstServerPost.id,
        postC1.id,
      ]);
      expect(find.text('Ảnh người lạ D1'), findsNothing);

      // Filter by Friend A (by-friend) and Friend C:
      await feedB.setFriend(userA.id);
      await tester.pump(const Duration(milliseconds: 100));
      expect(feedB.items.map((p) => p.id).toList(), [
        postA2.id,
        firstServerPost.id,
      ]);

      await feedB.setFriend(userC.id);
      await tester.pump(const Duration(milliseconds: 100));
      expect(feedB.items.map((p) => p.id).toList(), [postC1.id]);

      await feedB.setFriend(null);
      await tester.pump(const Duration(milliseconds: 100));
      expect(feedB.items.map((p) => p.id).toList(), [
        postA2.id,
        firstServerPost.id,
        postC1.id,
      ]);

      // 3. Media Detail & Real Native Save via GalGalleryAdapter:
      final detailController = MediaDetailController(
        api: apiB,
        postId: firstServerPost.id,
        onUnavailable: feedB.removePost,
        gallery: GalGalleryAdapter(),
        downloader: DioMediaDownloader(),
        isSessionActive: () => feedB.canViewPost(firstServerPost),
      );
      unawaited(
        Navigator.of(tester.element(find.byType(AppShell))).push<void>(
          MaterialPageRoute(
            builder: (_) => MediaDetailScreen(
              api: apiB,
              postId: firstServerPost.id,
              onUnavailable: feedB.removePost,
              controller: detailController,
              isSessionActive: () => feedB.canViewPost(firstServerPost),
              accessChanges: feedB,
            ),
          ),
        ),
      );
      await _pumpUntil(
        tester,
        () => !detailController.loading && detailController.post != null,
      );
      expect(find.byType(InteractiveViewer), findsOneWidget);
      expect(
        find.descendant(
          of: find.byType(MediaDetailScreen),
          matching: find.text('Khoảnh khắc An A1 ấm áp'),
        ),
        findsOneWidget,
      );

      await tester.tap(find.byTooltip('Lưu vào thư viện'));
      await _pumpUntil(
        tester,
        () => !detailController.saving && detailController.message != null,
      );
      expect(detailController.message, 'Đã lưu ảnh vào thư viện.');
      expect(find.text('Đã lưu ảnh vào thư viện.'), findsOneWidget);

      Navigator.of(tester.element(find.byType(MediaDetailScreen))).pop();
      await _pumpUntil(
        tester,
        () => find.byType(MediaDetailScreen).evaluate().isEmpty,
      );
      detailController.dispose();

      // 4. Offline Cache Fallback (simulating airplane mode / disconnected network):
      final offlineApiB = PostsApi(
        ApiClient(
          storage: _IsolatedSessionStorage(),
          client: MockClient((_) async => http.Response('offline', 503)),
        ),
      );
      final offlineFeedB = FeedController(
        api: offlineApiB,
        accountId: userB.id,
        cache: cacheB,
      );
      await offlineFeedB.refresh();
      expect(offlineFeedB.offline, isTrue);
      expect(offlineFeedB.items.map((p) => p.id).toList(), [
        postA2.id,
        firstServerPost.id,
        postC1.id,
      ]);
      expect(offlineFeedB.previewFor(firstServerPost.id), isNotEmpty);

      // 5. Post Deletion Invalidation across Feed, Detail, and Cross-Filter Offline Cache:
      // Switch B to Friend A filter, then A deletes firstServerPost (while postA2 remains).
      await feedB.setFriend(userA.id);
      await tester.pump(const Duration(milliseconds: 100));
      final delRes = await authA.apiClient.delete(
        '/api/v1/posts/${firstServerPost.id}',
      );
      expect(delRes.success, isTrue);
      await feedB.refresh();
      await tester.pump(const Duration(milliseconds: 100));

      expect(feedB.items.map((p) => p.id).toList(), [postA2.id]);
      expect(feedB.canViewPost(firstServerPost), isFalse);

      // Verify deleted post does NOT resurrect from 'all' filter offline cache:
      await offlineFeedB.refresh();
      expect(offlineFeedB.items.map((p) => p.id).toList(), [
        postA2.id,
        postC1.id,
      ]);
      expect(offlineFeedB.previewFor(firstServerPost.id), isNull);
      offlineFeedB.dispose();

      // 6. Block User Invalidation across Feed, Detail, and Offline Cache via FriendsScreen UI:
      await feedB.setFriend(null);
      await tester.pump(const Duration(milliseconds: 100));
      expect(feedB.items.map((p) => p.id).toList(), [postA2.id, postC1.id]);

      await tester.tap(find.text('Bạn bè'));
      await _pumpUntil(
        tester,
        () => find.widgetWithText(ListTile, 'Author An').evaluate().isNotEmpty,
      );
      final anTileMenu = find.descendant(
        of: find.widgetWithText(ListTile, 'Author An'),
        matching: find.byType(PopupMenuButton<String>),
      );
      await tester.tap(anTileMenu);
      await _pumpUntil(
        tester,
        () => find.text('Chặn người này').evaluate().isNotEmpty,
      );
      await tester.tap(find.text('Chặn người này'));
      await _pumpUntil(
        tester,
        () => find
            .widgetWithText(FilledButton, 'Chặn người dùng')
            .evaluate()
            .isNotEmpty,
      );
      await tester.tap(find.widgetWithText(FilledButton, 'Chặn người dùng'));
      await _pumpUntil(tester, () => feedB.items.length == 1 && !feedB.loading);

      expect(feedB.items.map((p) => p.id).toList(), [postC1.id]);
      expect(feedB.canViewPost(postA2), isFalse);
      final cachedAfterBlock = await cacheB.read(null);
      expect(cachedAfterBlock?.posts.map((p) => p.id).toList(), [postC1.id]);

      // 7. Account Isolation on Account Switch & Logout:
      final draftStoreB = LocalComposerDraftStore(userB.id);
      await draftStoreB.write({
        'version': 1,
        'accountId': userB.id,
        'clientRequestId': '22222222-3333-4444-8555-666666666666',
        'caption': 'Draft riêng của Bình',
        'settings': const PhotoSettings().toJson(),
      });
      expect(await draftStoreB.read(), isNotNull);

      final composerC = ComposerController(api: apiC, accountId: userC.id);
      await composerC.initialize();
      expect(composerC.caption, isEmpty);
      expect(composerC.source, isNull);
      composerC.dispose();

      await authB.logout();
      await tester.pump(const Duration(milliseconds: 200));
      expect((await cacheB.read(null))?.posts ?? [], isEmpty);
      await draftStoreB.remove();

      await tester.pumpWidget(const SizedBox.shrink());
      composerA.dispose();
      feedA.dispose();
      feedB.dispose();
      await storeA.clear();
      await storeB.clear();
    },
  );
}
