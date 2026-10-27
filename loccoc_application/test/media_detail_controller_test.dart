import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/core/models/post_models.dart';
import 'package:loccoc_application/features/feed/media_adapters.dart';
import 'package:loccoc_application/features/feed/media_detail_controller.dart';

import 'feed_controller_test.dart' show TestPostsApi, TestDownloader;

class TestGallery implements GalleryAdapter {
  @override
  bool supported = true;
  GalleryAccess access = GalleryAccess.allowed;
  int saved = 0;
  Uint8List? savedBytes;
  Future<void> Function()? onPermission;
  @override
  Future<GalleryAccess> requestAccess() async {
    await onPermission?.call();
    return access;
  }

  @override
  Future<void> save(Uint8List bytes, {required String name}) async {
    saved++;
    savedBytes = bytes;
  }
}

class DelayedDownloader extends TestDownloader {
  final started = Completer<void>();
  final result = Completer<Uint8List>();
  @override
  Future<Uint8List> download(
    String url, {
    required int maxBytes,
    required Duration timeout,
  }) {
    started.complete();
    return result.future;
  }
}

void main() {
  late TestPostsApi api;
  late TestGallery gallery;
  late TestDownloader downloader;
  late MediaDetailController controller;
  final removed = <int>[];
  setUp(() {
    api = TestPostsApi();
    gallery = TestGallery();
    downloader = TestDownloader();
    removed.clear();
    controller = MediaDetailController(
      api: api,
      postId: 7,
      gallery: gallery,
      downloader: downloader,
      onUnavailable: (id) async {
        removed.add(id);
      },
    );
  });
  tearDown(() {
    controller.dispose();
  });
  test('detail 404 clears content and removes feed snapshot', () async {
    await controller.load();
    expect(controller.post?.id, 7);
    api.onDetail = (_) async =>
        throw const PostApiException(statusCode: 404, message: 'deleted');
    await controller.load();
    expect(controller.post, isNull);
    expect(controller.unavailable, isTrue);
    expect(removed, [7]);
  });
  test(
    'fresh authorization denial before save never downloads cached image',
    () async {
      await controller.load();
      api.onDetail = (_) async =>
          throw const PostApiException(statusCode: 403, message: 'blocked');
      expect(await controller.save(), isFalse);
      expect(downloader.calls, 0);
      expect(gallery.saved, 0);
      expect(controller.unavailable, isTrue);
      expect(removed, [7]);
    },
  );
  test(
    'denied or restricted Photos permission provides settings guidance',
    () async {
      gallery.access = GalleryAccess.denied;
      expect(await controller.save(), isFalse);
      expect(gallery.saved, 0);
      expect(downloader.calls, 0);
      expect(controller.message, contains('Cài đặt'));
      gallery.access = GalleryAccess.restricted;
      expect(await controller.save(), isFalse);
      expect(controller.message, contains('hạn chế'));
    },
  );
  test('revalidates after permission dialog before downloading', () async {
    gallery.onPermission = () async {
      api.onDetail = (_) async =>
          throw const PostApiException(statusCode: 403, message: 'revoked');
    };
    expect(await controller.save(), isFalse);
    expect(downloader.calls, 0);
    expect(gallery.saved, 0);
    expect(removed, [7]);
  });
  test(
    'revalidates after download and never saves when content became blocked',
    () async {
      final slow = DelayedDownloader();
      controller.dispose();
      controller = MediaDetailController(
        api: api,
        postId: 7,
        gallery: gallery,
        downloader: slow,
        onUnavailable: (id) async {
          removed.add(id);
        },
      );
      final save = controller.save();
      await slow.started.future.timeout(const Duration(seconds: 1));
      api.onDetail = (_) async =>
          throw const PostApiException(statusCode: 403, message: 'blocked');
      slow.result.complete(Uint8List.fromList([1, 2]));
      expect(await save, isFalse);
      expect(gallery.saved, 0);
      expect(removed, [7]);
    },
  );
  test(
    'success saves only current authorized URL and suppresses duplicate saves',
    () async {
      final slow = DelayedDownloader();
      controller.dispose();
      controller = MediaDetailController(
        api: api,
        postId: 7,
        gallery: gallery,
        downloader: slow,
        onUnavailable: (id) async {
          removed.add(id);
        },
      );
      final save = controller.save();
      await slow.started.future.timeout(const Duration(seconds: 1));
      expect(await controller.save(), isFalse);
      slow.result.complete(Uint8List.fromList([4, 5]));
      expect(await save, isTrue);
      expect(gallery.saved, 1);
      expect(gallery.savedBytes, [4, 5]);
    },
  );
  test(
    'web unsupported feedback never invokes permission or network',
    () async {
      gallery.supported = false;
      api.onDetail = (_) async => throw StateError('must not access network');
      expect(await controller.save(), isFalse);
      expect(gallery.saved, 0);
      expect(controller.message, contains('Android hoặc iOS'));
    },
  );
  test(
    'disposed or signed-out controller does not save after a pending download',
    () async {
      var active = true;
      final slow = DelayedDownloader();
      controller.dispose();
      controller = MediaDetailController(
        api: api,
        postId: 7,
        gallery: gallery,
        downloader: slow,
        isSessionActive: () => active,
        onUnavailable: (id) async {
          removed.add(id);
        },
      );
      final save = controller.save();
      await slow.started.future.timeout(const Duration(seconds: 1));
      active = false;
      slow.result.complete(Uint8List.fromList([4, 5]));
      expect(await save, isFalse);
      expect(gallery.saved, 0);
    },
  );

  test('changed media URL after download requires retry instead of saving old bytes', () async {
    var calls = 0;
    api.onDetail = (id) async {
      calls++;
      return PostModel.fromJson({
        'id': id,
        'userId': 2,
        'mediaType': 'IMAGE',
        'mediaUrl': calls >= 3
            ? 'https://media.test/new.jpg'
            : 'https://media.test/old.jpg',
        'status': 'ACTIVE',
        'createdAt': '2026-10-25T00:00:00Z',
      });
    };
    expect(await controller.save(), isFalse);
    expect(gallery.saved, 0);
    expect(controller.message, contains('thay đổi'));
  });
  test('social revocation immediately removes loaded detail', () async {
    await controller.load();
    expect(controller.post?.id, 7);
    await controller.invalidateAccess();
    expect(controller.post, isNull);
    expect(controller.unavailable, isTrue);
    expect(await controller.save(), isFalse);
    expect(removed, [7]);
  });
  test(
    'oversized injected download is rejected even if transport ignores bound',
    () async {
      downloader.bytes = Uint8List(10 * 1024 * 1024 + 1);
      expect(await controller.save(), isFalse);
      expect(gallery.saved, 0);
      expect(controller.message, contains('dung lượng'));
    },
  );
}
