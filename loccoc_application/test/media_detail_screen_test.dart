import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/core/models/post_models.dart';
import 'package:loccoc_application/features/feed/media_detail_controller.dart';
import 'package:loccoc_application/features/feed/media_detail_screen.dart';

import 'feed_controller_test.dart' show TestPostsApi, TestDownloader;
import 'media_detail_controller_test.dart' show TestGallery;

void main() {
  testWidgets('detail revalidates then removes unauthorized content', (
    tester,
  ) async {
    final api = TestPostsApi();
    final result = Completer<PostModel>();
    api.onDetail = (_) => result.future;
    final controller = MediaDetailController(
      api: api,
      postId: 7,
      downloader: TestDownloader(),
      gallery: TestGallery(),
      onUnavailable: (_) {},
    );
    await tester.pumpWidget(
      MaterialApp(
        home: MediaDetailScreen(
          api: api,
          postId: 7,
          controller: controller,
          onUnavailable: (_) {},
        ),
      ),
    );
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    result.completeError(
      const PostApiException(statusCode: 403, message: 'blocked'),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('không còn khả dụng'), findsOneWidget);
    expect(find.byType(InteractiveViewer), findsNothing);
    expect(
      tester.widget<IconButton>(find.byType(IconButton).first).onPressed,
      isNull,
    );
    await tester.pumpWidget(const SizedBox());
    controller.dispose();
  });
}
