import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/core/models/post_models.dart';
import 'package:loccoc_application/core/models/user_models.dart';
import 'package:loccoc_application/core/network/api_client.dart';
import 'package:loccoc_application/core/network/posts_api.dart';
import 'package:loccoc_application/core/storage/post_local_store.dart';
import 'package:loccoc_application/features/feed/feed_cache.dart';
import 'package:loccoc_application/features/feed/feed_controller.dart';
import 'package:loccoc_application/features/feed/media_adapters.dart';

PostModel post(int id, {int author = 2}) => PostModel.fromJson({
  'id': id,
  'userId': author,
  'mediaUrl': 'https://media.test/$id.jpg',
  'mediaType': 'IMAGE',
  'status': 'ACTIVE',
  'authorName': 'Friend $author',
  'createdAt': '2026-10-25T00:00:00Z',
  'caption': 'Photo $id',
});
PostPage page(List<int> ids, {String? cursor}) => PostPage(
  items: ids.map((id) => post(id)).toList(),
  nextCursor: cursor,
  hasNext: cursor != null,
);

class MemoryPostStore extends PostLocalStore {
  MemoryPostStore({super.accountId = 1});
  final values = <String, Map<String, dynamic>>{};
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

class TestPostsApi extends PostsApi {
  TestPostsApi() : super(ApiClient());
  Future<PostPage> Function(int? friendId, String? cursor) onFeed = (
    _,
    _,
  ) async => page([]);
  Future<PostModel> Function(int id) onDetail = (id) async => post(id);
  @override
  Future<PostPage> feed({int? friendId, String? cursor, int limit = 20}) =>
      onFeed(friendId, cursor);
  @override
  Future<PostModel> detail(int id) => onDetail(id);
  @override
  Future<List<FriendSummary>> friends() async => [];
}

class TestDownloader implements MediaDownloader {
  Uint8List bytes = Uint8List.fromList([1, 2, 3]);
  int calls = 0;
  @override
  Future<Uint8List> download(
    String url, {
    required int maxBytes,
    required Duration timeout,
  }) async {
    calls++;
    return bytes;
  }
}

void main() {
  late TestPostsApi api;
  late MemoryPostStore store;
  late FeedCache cache;
  late FeedController controller;
  setUp(() {
    api = TestPostsApi();
    store = MemoryPostStore();
    cache = FeedCache(store: store);
    controller = FeedController(
      api: api,
      accountId: 1,
      cache: cache,
      downloader: TestDownloader(),
    );
  });
  tearDown(() {
    controller.dispose();
  });

  test('filter switch ignores old network results and old errors', () async {
    final old = Completer<PostPage>();
    api.onFeed = (friend, _) =>
        friend == null ? old.future : Future.value(page([20]));
    final first = controller.refresh();
    await Future<void>.delayed(Duration.zero);
    await controller.setFriend(2);
    old.complete(page([10]));
    await first;
    expect(controller.items.map((p) => p.id), [20]);
    expect(controller.friendId, 2);
  });
  test(
    'refresh suppresses inflight pagination and deduplicates fresh pages',
    () async {
      final more = Completer<PostPage>();
      api.onFeed = (_, cursor) async =>
          cursor == null ? page([1, 2], cursor: 'a') : more.future;
      await controller.refresh();
      final pending = controller.loadMore();
      await controller.refresh();
      more.complete(page([2, 3]));
      await pending;
      expect(controller.items.map((p) => p.id), [1, 2]);
      api.onFeed = (_, cursor) async => page([2, 3]);
      await controller.loadMore();
      expect(controller.items.map((p) => p.id), [1, 2, 3]);
    },
  );
  test(
    'successful empty refresh deletes old visible and offline images',
    () async {
      api.onFeed = (_, _) async => page([1]);
      await controller.refresh();
      api.onFeed = (_, _) async => page([]);
      await controller.refresh();
      expect(controller.items, isEmpty);
      api.onFeed = (_, _) async => throw Exception('offline');
      await controller.refresh();
      expect(controller.items, isEmpty);
      expect(controller.offline, isTrue);
      expect((await cache.read(null))?.previews, isEmpty);
    },
  );
  test('403 clears cached filter and prevents offline resurrection', () async {
    api.onFeed = (_, _) async => page([1]);
    await controller.setFriend(2);
    api.onFeed = (_, _) async =>
        throw const PostApiException(statusCode: 403, message: 'blocked');
    await controller.refresh();
    expect(controller.items, isEmpty);
    expect(controller.forbidden, isTrue);
    api.onFeed = (_, _) async => throw Exception('offline');
    await controller.refresh();
    expect(controller.items, isEmpty);
    expect(await cache.read(2), isNull);
  });
  test('authoritative denied filter cannot resurrect same post in all-friends offline cache', () async {
    api.onFeed = (_, _) async => page([1]);
    await controller.refresh();
    await controller.setFriend(2);
    expect((await cache.read(null))!.posts.single.id, 1);
    api.onFeed = (_, _) async =>
        throw const PostApiException(statusCode: 403, message: 'blocked');
    await controller.refresh();
    api.onFeed = (_, _) async => throw Exception('offline');
    await controller.setFriend(null);
    expect(controller.items, isEmpty);
    expect(await cache.read(null), isNull);
  });
  test('successful empty filter clears cross-filter stale content before offline switch', () async {
    api.onFeed = (_, _) async => page([1]);
    await controller.refresh();
    await controller.setFriend(2);
    api.onFeed = (_, _) async => page([]);
    await controller.refresh();
    api.onFeed = (_, _) async => throw Exception('offline');
    await controller.setFriend(null);
    expect(controller.items, isEmpty);
    expect((await cache.read(2))!.posts, isEmpty);
  });
  test('new controller restores first page bytes offline but never another account', () async {
    api.onFeed = (_, _) async => page([1], cursor: 'a');
    await controller.refresh();
    controller.dispose();
    controller = FeedController(
      api: api,
      accountId: 1,
      cache: cache,
      downloader: TestDownloader(),
    );
    api.onFeed = (_, _) async => throw Exception('offline');
    await controller.refresh();
    expect(controller.items.single.id, 1);
    expect(controller.previewFor(1), [1, 2, 3]);
    expect(controller.offline, isTrue);
    expect(controller.hasNext, isFalse);
    final other = FeedController(
      api: api,
      accountId: 9,
      cache: FeedCache(store: MemoryPostStore(accountId: 9)),
      downloader: TestDownloader(),
    );
    await other.refresh();
    expect(other.items, isEmpty);
    other.dispose();
  });
  test(
    'social invalidation prevents late old network and offline blocked posts',
    () async {
      api.onFeed = (_, _) async => page([1]);
      await controller.refresh();
      expect(controller.items.single.id, 1);
      final old = Completer<PostPage>();
      api.onFeed = (_, _) => old.future;
      final pending = controller.refresh();
      await Future<void>.delayed(Duration.zero);
      api.onFeed = (_, _) async => throw Exception('offline');
      await controller.invalidateFriendships({3});
      old.complete(page([1]));
      await pending;
      expect(controller.items, isEmpty);
      expect(await cache.read(null), isNull);
    },
  );
  test(
    'unavailable detail removes item and preview from every cached filter',
    () async {
      api.onFeed = (_, _) async => page([1, 2]);
      await controller.refresh();
      await controller.setFriend(2);
      await controller.removePost(1);
      expect(controller.items.map((p) => p.id), [2]);
      expect((await cache.read(null))!.posts.map((p) => p.id), [2]);
      expect((await cache.read(2))!.previews.containsKey(1), isFalse);
    },
  );

  test('late old forbidden response cannot clear a newer filter', () async {
    final old = Completer<PostPage>();
    api.onFeed = (friend, _) =>
        friend == null ? old.future : Future.value(page([2]));
    final pending = controller.refresh();
    await controller.setFriend(2);
    old.completeError(
      const PostApiException(statusCode: 403, message: 'old forbidden'),
    );
    await pending;
    expect(controller.items.single.id, 2);
    expect(controller.forbidden, isFalse);
    expect(controller.loading, isFalse);
  });
  test('pagination authorization failure clears visible items and offline snapshot', () async {
    api.onFeed = (_, cursor) async => cursor == null
        ? page([1], cursor: 'a')
        : throw const PostApiException(statusCode: 404, message: 'deleted');
    await controller.refresh();
    await controller.loadMore();
    expect(controller.items, isEmpty);
    expect(controller.forbidden, isTrue);
    expect(await cache.read(null), isNull);
  });
  test('logout clears cache and rejects late responses', () async {
    api.onFeed = (_, _) async => page([1]);
    await controller.refresh();
    final old = Completer<PostPage>();
    api.onFeed = (_, _) => old.future;
    final pending = controller.refresh();
    await controller.invalidateSession();
    old.complete(page([1]));
    await pending;
    expect(controller.items, isEmpty);
    expect(await cache.read(null), isNull);
    expect(controller.canViewPost(post(1)), isFalse);
  });
  test('cache bounds filters, posts, and encoded preview bytes', () async {
    final many = List.generate(30, (i) => post(i));
    for (var friend = 1; friend <= 9; friend++) {
      await cache.write(
        friend,
        FeedSnapshot(
          posts: many,
          savedAt: DateTime.now(),
          previews: {for (var p in many) p.id: Uint8List(256 * 1024)},
        ),
      );
    }
    expect(await cache.read(1), isNull);
    final latest = (await cache.read(9))!;
    expect(latest.posts.length, lessThanOrEqualTo(20));
    final encoded = jsonEncode(store.values);
    expect(encoded.length, lessThan(3 * 1024 * 1024));
  });
}
