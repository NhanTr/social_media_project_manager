import 'dart:convert';
import 'dart:typed_data';

import '../../core/models/post_models.dart';
import '../../core/storage/post_local_store.dart';

class FeedSnapshot {
  final List<PostModel> posts;
  final DateTime savedAt;
  final Map<int, Uint8List> previews;
  FeedSnapshot({
    required this.posts,
    required this.savedAt,
    this.previews = const {},
  });
}

/// One bounded document per account; the store supplies atomic account isolation.
class FeedCache {
  FeedCache({required this.store});
  final PostLocalStore store;
  static const maxSnapshots = 6;
  static const maxPosts = 20;
  static const maxPreviewBytes = 256 * 1024;
  static const maxTotalBytes = 2 * 1024 * 1024;
  static const _key = 'feed_snapshots_v1';
  Future<void> _pending = Future<void>.value();

  Future<T> _run<T>(Future<T> Function() action) {
    final result = _pending.then((_) => action());
    _pending = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return result;
  }

  String _filter(int? id) => id?.toString() ?? 'all';
  Future<Map<String, dynamic>> _load() async {
    final value = await store.read(_key);
    if (value == null || value['accountId'] != store.accountId) return {};
    return Map<String, dynamic>.from(value['snapshots'] as Map? ?? {});
  }

  Future<void> _save(Map<String, dynamic> snapshots) =>
      store.write(_key, {'accountId': store.accountId, 'snapshots': snapshots});

  Future<FeedSnapshot?> read(int? friendId) => _run(() async {
    try {
      final all = await _load();
      final raw = all[_filter(friendId)] as Map?;
      if (raw == null) return null;
      final posts = (raw['posts'] as List)
          .take(maxPosts)
          .map((p) => PostModel.fromJson(Map<String, dynamic>.from(p as Map)))
          .toList();
      final previews = <int, Uint8List>{};
      var total = 0;
      final images = raw['previews'] as Map? ?? {};
      for (final p in posts) {
        final encoded = images[p.id.toString()];
        if (encoded is! String ||
            encoded.length > (maxPreviewBytes * 4 ~/ 3) + 4) {
          continue;
        }
        final bytes = base64Decode(encoded);
        if (bytes.length > maxPreviewBytes ||
            total + bytes.length > maxTotalBytes) {
          continue;
        }
        previews[p.id] = bytes;
        total += bytes.length;
      }
      return FeedSnapshot(
        posts: posts,
        savedAt: DateTime.parse(raw['savedAt'] as String),
        previews: previews,
      );
    } catch (_) {
      return null;
    }
  });

  Future<Set<int>> write(
    int? friendId,
    FeedSnapshot snapshot, {
    bool hasMore = false,
  }) => _run(() async {
    final all = await _load();
    final filterKey = _filter(friendId);
    final posts = snapshot.posts.take(maxPosts).toList();
    // Reconcile against the complete response, before bounding persisted previews.
    final currentIds = snapshot.posts.map((p) => p.id).toSet();
    final evictedIds = <int>{};
    final oldestCurrent = snapshot.posts.isEmpty ? null : snapshot.posts.last;
    for (final raw in all.values.cast<Map>()) {
      for (final json in (raw['posts'] as List? ?? [])) {
        final previous = PostModel.fromJson(
          Map<String, dynamic>.from(json as Map),
        );
        if (currentIds.contains(previous.id) ||
            (friendId != null && previous.userId != friendId)) {
          continue;
        }
        // A partial page is authoritative only down to its complete cursor key.
        if (!hasMore ||
            oldestCurrent != null &&
                previous.compareFeedPosition(oldestCurrent) <= 0) {
          evictedIds.add(previous.id);
        }
      }
    }
    // Insertion order is recency order and is deterministic across restarts.
    all.remove(filterKey);
    if (evictedIds.isNotEmpty) {
      for (final raw in all.values.cast<Map>()) {
        (raw['posts'] as List).removeWhere(
          (p) => evictedIds.contains(((p as Map)['id'] as num).toInt()),
        );
        final previews = raw['previews'] as Map;
        for (final id in evictedIds) {
          previews.remove(id.toString());
        }
      }
    }
    all[filterKey] = {
      'savedAt': snapshot.savedAt.toUtc().toIso8601String(),
      'posts': posts.map((p) => p.toJson()).toList(),
      'previews': {
        for (final p in posts)
          if (snapshot.previews[p.id] != null &&
              snapshot.previews[p.id]!.length <= maxPreviewBytes)
            p.id.toString(): base64Encode(snapshot.previews[p.id]!),
      },
    };
    while (all.length > maxSnapshots) {
      all.remove(all.keys.first);
    }
    var bytes = 0;
    for (final key in all.keys.toList().reversed) {
      final raw = all[key] as Map<String, dynamic>;
      final images = raw['previews'] as Map<String, dynamic>;
      for (final id in images.keys.toList()) {
        final encoded = images[id] as String;
        // Estimate encoded bytes conservatively; total raw bytes stays below bound.
        final size = encoded.length * 3 ~/ 4;
        if (bytes + size > maxTotalBytes) {
          images.remove(id);
        } else {
          bytes += size;
        }
      }
    }
    await _save(all);
    return evictedIds;
  });

  Future<void> remove(int? friendId) => _run(() async {
    final all = await _load();
    all.remove(_filter(friendId));
    await _save(all);
  });
  Future<void> removePost(int id) => _run(() async {
    final all = await _load();
    for (final raw in all.values.cast<Map>()) {
      (raw['posts'] as List).removeWhere((p) => (p as Map)['id'] == id);
      (raw['previews'] as Map).remove(id.toString());
    }
    await _save(all);
  });
  Future<void> clear() => _run(() => store.remove(_key));
}
