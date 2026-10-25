import 'package:flutter/foundation.dart';

import '../../core/models/post_models.dart';
import '../../core/network/posts_api.dart';
import '../../core/storage/post_local_store.dart';
import 'feed_cache.dart';
import 'media_adapters.dart';

class FeedController extends ChangeNotifier {
  FeedController({
    required this.api,
    required int accountId,
    FeedCache? cache,
    MediaDownloader? downloader,
  }) : cache = cache ?? FeedCache(store: PostLocalStore(accountId: accountId)),
       downloader = downloader ?? DioMediaDownloader();
  final PostsApi api;
  final FeedCache cache;
  final MediaDownloader downloader;
  List<PostModel> _items = [];
  List<PostModel> get items => List.unmodifiable(_items);
  int? friendId;
  String? _cursor;
  bool hasNext = false;
  bool loading = false;
  bool loadingMore = false;
  bool offline = false;
  bool forbidden = false;
  String? error;
  DateTime? lastSuccess;
  final Map<int, Uint8List> _previews = {};
  final Set<int> _removed = {};
  Set<int>? _allowedAuthors;
  int _generation = 0;
  bool _disposed = false;
  bool _sessionInvalid = false;
  bool _current(int generation) =>
      !_disposed && !_sessionInvalid && generation == _generation;
  void _emit() {
    if (!_disposed) notifyListeners();
  }

  Uint8List? previewFor(int id) => _previews[id];
  bool canViewPost(PostModel post) =>
      !_disposed && !_sessionInvalid && !forbidden && _allowed(post);
  bool _allowed(PostModel p) =>
      !_removed.contains(p.id) &&
      (_allowedAuthors == null || _allowedAuthors!.contains(p.userId));
  List<PostModel> _unique(Iterable<PostModel> posts) {
    final seen = <int>{};
    return posts.where((p) => _allowed(p) && seen.add(p.id)).toList();
  }

  Future<void> setFriend(int? id) async {
    if (id == friendId) return;
    friendId = id;
    _items = [];
    _previews.clear();
    lastSuccess = null;
    await refresh();
  }

  Future<void> refresh() async {
    if (_disposed || _sessionInvalid) return;
    final generation = ++_generation;
    final filter = friendId;
    loading = true;
    loadingMore = false;
    error = null;
    forbidden = false;
    _cursor = null;
    hasNext = false;
    _emit();
    try {
      final page = await api.feed(friendId: filter);
      if (!_current(generation)) return;
      _items = _unique(page.items);
      _cursor = page.nextCursor;
      hasNext = page.hasNext && _cursor != null;
      _previews.clear();
      offline = false;
      lastSuccess = DateTime.now();
      loading = false;
      _emit();
      // An authoritative absence can cover items in several filter snapshots.
      if (_items.isEmpty) {
        await _cacheSafely(cache.clear);
        if (!_current(generation)) return;
      }
      // Write metadata first: even process termination must not resurrect deleted posts.
      await _cacheSafely(
        () => cache.write(
          filter,
          FeedSnapshot(posts: _items, savedAt: lastSuccess!),
        ),
      );
      if (!_current(generation) || _items.isEmpty) return;
      final first = _items.take(FeedCache.maxPosts).toList();
      final results = await Future.wait(
        first.map((p) async {
          try {
            final bytes = await downloader.download(
              p.thumbnailUrl ?? p.mediaUrl,
              maxBytes: FeedCache.maxPreviewBytes,
              timeout: const Duration(seconds: 4),
            );
            return bytes.isNotEmpty && bytes.length <= FeedCache.maxPreviewBytes
                ? MapEntry(p.id, bytes)
                : null;
          } catch (_) {
            return null;
          }
        }),
      );
      if (!_current(generation)) return;
      var budget = 0;
      for (final entry in results.whereType<MapEntry<int, Uint8List>>()) {
        if (_removed.contains(entry.key) ||
            budget + entry.value.length > FeedCache.maxTotalBytes) {
          continue;
        }
        _previews[entry.key] = entry.value;
        budget += entry.value.length;
      }
      await _cacheSafely(
        () => cache.write(
          filter,
          FeedSnapshot(
            posts: first.where(_allowed).toList(),
            savedAt: lastSuccess!,
            previews: Map.of(_previews),
          ),
        ),
      );
      if (_current(generation)) _emit();
    } catch (e) {
      if (!_current(generation)) return;
      if (e is PostApiException &&
          (e.statusCode == 403 || e.statusCode == 404 || e.statusCode == 401)) {
        _items = [];
        _previews.clear();
        lastSuccess = null;
        forbidden = true;
        offline = false;
        await _cacheSafely(cache.clear);
        if (!_current(generation)) return;
        error = 'Nội dung không còn khả dụng hoặc bạn không có quyền xem.';
      } else {
        // A server error is not authoritative absence. Display only bounded first-page cache.
        FeedSnapshot? snapshot;
        try {
          snapshot = await cache.read(filter);
        } catch (_) {
          /* Cache may be unavailable. */
        }
        if (!_current(generation)) return;
        _items = _unique(snapshot?.posts ?? []);
        _previews
          ..clear()
          ..addAll(snapshot?.previews ?? {});
        lastSuccess = snapshot?.savedAt;
        offline = true;
        error = 'Không thể tải bảng tin. Kéo xuống hoặc thử lại khi có mạng.';
      }
      loading = false;
      _emit();
    }
  }

  Future<void> loadMore() async {
    if (_disposed ||
        _sessionInvalid ||
        loading ||
        loadingMore ||
        !hasNext ||
        offline) {
      return;
    }
    final generation = _generation;
    final cursor = _cursor;
    loadingMore = true;
    error = null;
    _emit();
    try {
      final page = await api.feed(friendId: friendId, cursor: cursor);
      if (!_current(generation)) return;
      _items = _unique([..._items, ...page.items]);
      _cursor = page.nextCursor;
      hasNext = page.hasNext && _cursor != null && _cursor != cursor;
    } catch (e) {
      if (!_current(generation)) return;
      if (e is PostApiException &&
          (e.statusCode == 401 || e.statusCode == 403 || e.statusCode == 404)) {
        ++_generation;
        _items = [];
        _previews.clear();
        hasNext = false;
        forbidden = true;
        offline = false;
        await _cacheSafely(cache.clear);
      }
      error = forbidden
          ? 'Nội dung không còn khả dụng.'
          : 'Không thể tải thêm. Hãy thử lại.';
    }
    if (!_disposed &&
        !_sessionInvalid &&
        (generation == _generation || forbidden)) {
      loadingMore = false;
      _emit();
    }
  }

  Future<void> removePost(int id) async {
    _removed.add(id);
    _items.removeWhere((p) => p.id == id);
    _previews.remove(id);
    _emit();
    await _cacheSafely(() => cache.removePost(id));
  }

  Future<void> invalidateFriendships(Set<int> currentFriendIds) async {
    ++_generation;
    _allowedAuthors = Set.of(currentFriendIds);
    _items = _unique(_items);
    _previews.removeWhere((id, _) => !_items.any((p) => p.id == id));
    if (friendId != null && !currentFriendIds.contains(friendId)) {
      friendId = null;
    }
    _emit();
    await _cacheSafely(cache.clear);
    await refresh();
  }

  Future<void> invalidateSession() async {
    _sessionInvalid = true;
    ++_generation;
    _items = [];
    _previews.clear();
    loading = false;
    loadingMore = false;
    _emit();
    await _cacheSafely(cache.clear);
  }

  Future<void> _cacheSafely(Future<void> Function() action) async {
    try {
      await action();
    } catch (_) {
      /* Storage failure must not hide network results. */
    }
  }

  @override
  void dispose() {
    _disposed = true;
    ++_generation;
    super.dispose();
  }
}
