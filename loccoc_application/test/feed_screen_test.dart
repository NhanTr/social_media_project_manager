import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/core/models/post_models.dart';
import 'package:loccoc_application/features/feed/feed_cache.dart';
import 'package:loccoc_application/features/feed/feed_controller.dart';
import 'package:loccoc_application/features/feed/feed_screen.dart';

import 'feed_controller_test.dart'
    show TestPostsApi, TestDownloader, MemoryPostStore, page;

void main() {
  late TestPostsApi api;
  late FeedController controller;
  setUp(() {
    api = TestPostsApi();
    final downloader = TestDownloader()
      ..bytes = base64Decode(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==',
      );
    controller = FeedController(
      api: api,
      accountId: 1,
      cache: FeedCache(store: MemoryPostStore()),
      downloader: downloader,
    );
  });
  tearDown(() {
    controller.dispose();
  });
  Widget screen({GlobalKey<FeedScreenState>? key, VoidCallback? onCompose}) =>
      MaterialApp(
        home: FeedScreen(
          key: key,
          api: api,
          accountId: 1,
          controller: controller,
          onCompose: onCompose ?? () {},
        ),
      );

  testWidgets('loading uses skeleton then empty action opens composer', (
    tester,
  ) async {
    final response = Completer<PostPage>();
    api.onFeed = (_, _) => response.future;
    var compose = 0;
    await tester.pumpWidget(
      screen(
        onCompose: () {
          compose++;
        },
      ),
    );
    expect(find.byKey(const Key('feed-skeleton')), findsOneWidget);
    response.complete(page([]));
    await tester.pumpAndSettle();
    expect(find.text('Chưa có ảnh từ bạn bè'), findsOneWidget);
    await tester.tap(find.byTooltip('Chia sẻ ảnh'));
    expect(compose, 1);
  });
  testWidgets(
    'offline snapshot renders durable memory bytes and stale banner',
    (tester) async {
      api.onFeed = (_, _) async => page([1]);
      await tester.runAsync(() => controller.refresh());
      api.onFeed = (_, _) async => throw Exception('offline');
      await tester.pumpWidget(screen());
      await tester.pumpAndSettle();
      await tester.runAsync(() async {
        await Future<void>.value();
      });
      await tester.pumpAndSettle();
      expect(find.textContaining('Ngoại tuyến'), findsOneWidget);
      expect(find.text('Photo 1'), findsOneWidget);
      expect(find.byType(Image), findsOneWidget);
      final provider = tester.widget<Image>(find.byType(Image)).image;
      expect(
        provider is ResizeImage ? provider.imageProvider : provider,
        isA<MemoryImage>(),
      );
    },
  );
  testWidgets('key refresh replaces deleted content with authoritative empty', (
    tester,
  ) async {
    api.onFeed = (_, _) async => page([1]);
    final key = GlobalKey<FeedScreenState>();
    await tester.pumpWidget(screen(key: key));
    await tester.pumpAndSettle();
    expect(find.text('Photo 1'), findsOneWidget);
    api.onFeed = (_, _) async => page([]);
    final refreshing = key.currentState!.refresh();
    await tester.pumpAndSettle();
    await tester.runAsync(() async {
      await Future<void>.value();
    });
    await tester.pumpAndSettle();
    await refreshing;
    expect(find.text('Photo 1'), findsNothing);
    expect(find.text('Chưa có ảnh từ bạn bè'), findsOneWidget);
  });
}
