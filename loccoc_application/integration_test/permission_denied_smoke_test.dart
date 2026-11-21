import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:image/image.dart' as img;
import 'package:integration_test/integration_test.dart';
import 'package:loccoc_application/core/network/api_client.dart';
import 'package:loccoc_application/core/network/posts_api.dart';
import 'package:loccoc_application/core/storage/post_local_store.dart';
import 'package:loccoc_application/core/storage/session_storage.dart';
import 'package:loccoc_application/features/composer/composer_adapters.dart';
import 'package:loccoc_application/features/composer/composer_controller.dart';
import 'package:loccoc_application/features/composer/composer_image.dart';
import 'package:loccoc_application/features/composer/composer_screen.dart';
import 'package:loccoc_application/features/feed/media_adapters.dart';
import 'package:loccoc_application/features/feed/media_detail_controller.dart';
import 'package:loccoc_application/features/feed/media_detail_screen.dart';

class _NoopSessionStorage extends SessionStorage {
  @override
  Future<String?> getAccessToken() async => 'test-token';
}

class _FastPhotoProcessor implements PhotoProcessor {
  const _FastPhotoProcessor();

  @override
  Future<PreparedPhoto> process(
    Uint8List source,
    PhotoSettings settings,
  ) async => PreparedPhoto(source, 64, 64);
}

class _FixedDownloader implements MediaDownloader {
  @override
  Future<Uint8List> download(
    String url, {
    required int maxBytes,
    required Duration timeout,
  }) async {
    final photo = img.Image(width: 32, height: 32);
    img.fill(photo, color: img.ColorRgb8(40, 80, 120));
    return Uint8List.fromList(img.encodeJpg(photo));
  }
}

Future<void> _pumpUntil(
  WidgetTester tester,
  bool Function() condition, {
  Duration timeout = const Duration(seconds: 15),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (!condition()) {
    if (DateTime.now().isAfter(deadline)) {
      fail('Timed out waiting for condition after $timeout');
    }
    await tester.pump(const Duration(milliseconds: 100));
  }
  await tester.pump();
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  const expectPhotosDenied = bool.fromEnvironment(
    'EXPECT_PHOTOS_DENIED',
    defaultValue: false,
  );

  testWidgets(
    'native camera permission denial shows actionable recovery guidance and preserves draft',
    (tester) async {
      const accountId = 99001;
      final localStore = PostLocalStore(accountId: accountId);
      await localStore.clear();
      final draftStore = LocalComposerDraftStore(accountId);
      final seedImage = img.Image(width: 64, height: 64);
      img.fill(seedImage, color: img.ColorRgb8(180, 90, 50));
      final seedBytes = Uint8List.fromList(img.encodeJpg(seedImage));
      await draftStore.write({
        'version': 1,
        'accountId': accountId,
        'clientRequestId': '11111111-2222-4333-8444-555555555555',
        'caption': 'Draft giữ nguyên khi từ chối quyền camera',
        'settings': const PhotoSettings(
          square: true,
          cropX: 0.4,
          cropY: 0.6,
          filter: PhotoFilter.warm,
        ).toJson(),
        'stage': 'editing',
        'source': base64Encode(seedBytes),
      });

      final api = PostsApi(
        ApiClient(
          storage: _NoopSessionStorage(),
          client: MockClient((_) async => http.Response('{}', 200)),
        ),
      );
      final controller = ComposerController(
        api: api,
        accountId: accountId,
        store: draftStore,
        picker: NativeComposerPhotoPicker(accountId: accountId),
        processor: const _FastPhotoProcessor(),
      );
      await controller.initialize();

      await tester.pumpWidget(
        MaterialApp(
          home: ComposerScreen(
            api: api,
            accountId: accountId,
            controller: controller,
          ),
        ),
      );
      await _pumpUntil(
        tester,
        () => controller.initialized && !controller.busy,
      );

      expect(controller.caption, 'Draft giữ nguyên khi từ chối quyền camera');

      await tester.tap(find.text('Máy ảnh'));
      await _pumpUntil(
        tester,
        () => !controller.busy && controller.error != null,
      );

      const expectedError =
          'Quyền máy ảnh hoặc thư viện ảnh đã bị từ chối. Mở Cài đặt ứng dụng, cấp quyền rồi thử lại.';
      expect(controller.error, expectedError);
      expect(controller.caption, 'Draft giữ nguyên khi từ chối quyền camera');
      expect(
        controller.clientRequestId,
        '11111111-2222-4333-8444-555555555555',
      );

      final scroll = find.descendant(
        of: find.byType(ComposerScreen),
        matching: find.byType(Scrollable),
      );
      await tester.scrollUntilVisible(
        find.text(expectedError),
        160,
        scrollable: scroll.first,
      );
      expect(find.text(expectedError), findsOneWidget);

      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await localStore.clear();
    },
  );

  if (expectPhotosDenied) {
    testWidgets(
      'native Photos add-only permission denial shows Settings guidance on MediaDetailScreen',
      (tester) async {
        final api = PostsApi(
          ApiClient(
            storage: _NoopSessionStorage(),
            client: MockClient((request) async {
              if (request.url.path == '/api/v1/posts/501') {
                return http.Response(
                  jsonEncode({
                    'id': 501,
                    'userId': 2,
                    'authorName': 'Ban Test',
                    'mediaUrl': 'https://example.invalid/photo.jpg',
                    'mediaType': 'IMAGE',
                    'caption': 'Anh kiem tra quyen Photos',
                    'status': 'ACTIVE',
                    'createdAt': '2026-10-27T12:00:00Z',
                  }),
                  200,
                  headers: const {
                    'content-type': 'application/json; charset=utf-8',
                  },
                );
              }
              return http.Response('{}', 404);
            }),
          ),
        );
        final detailController = MediaDetailController(
          api: api,
          postId: 501,
          onUnavailable: (_) {},
          gallery: GalGalleryAdapter(),
          downloader: _FixedDownloader(),
        );

        await tester.pumpWidget(
          MaterialApp(
            home: MediaDetailScreen(
              api: api,
              postId: 501,
              onUnavailable: (_) {},
              controller: detailController,
            ),
          ),
        );
        await _pumpUntil(
          tester,
          () => !detailController.loading && detailController.post != null,
        );

        await tester.tap(find.byTooltip('Lưu vào thư viện'));
        await _pumpUntil(
          tester,
          () => !detailController.saving && detailController.message != null,
        );

        const expectedDeniedText =
            'Quyền thêm ảnh bị từ chối. Mở Cài đặt > Ảnh và cho phép thêm ảnh; quyền truy cập giới hạn chỉ cần cho phép thêm.';
        expect(detailController.message, expectedDeniedText);
        expect(find.text(expectedDeniedText), findsOneWidget);

        await tester.pumpWidget(const SizedBox.shrink());
        detailController.dispose();
      },
    );
  }
}
