import 'dart:async';

import 'package:flutter/foundation.dart';

import 'notification_models.dart';
import 'notification_repository.dart';

class NotificationController extends ChangeNotifier {
  final NotificationRepository repository;
  final int accountId;
  NotificationController({required this.repository, required this.accountId}) {
    if (accountId <= 0 || repository.accountId != accountId) {
      throw ArgumentError('Notification repository account mismatch');
    }
  }
  List<NotificationItem> _items = [];
  List<NotificationItem> get items => List.unmodifiable(_items);
  int unreadCount = 0;
  NotificationItem? foregroundNotification;
  bool loading = false, loadingMore = false, markingAllRead = false;
  String? error;
  String? _cursor;
  bool get hasMore => _cursor != null;
  bool get isDemo => repository is DemoNotificationRepository;
  bool get demoOnline =>
      repository is! DemoNotificationRepository ||
      (repository as DemoNotificationRepository).online;
  StreamSubscription<NotificationItem>? _subscription;
  Future<void>? _initializing;
  bool _disposed = false;
  int _pageRequest = 0, _countRequest = 0, _eventRevision = 0;
  Future<void>? _countFuture;
  bool _countDirty = false;
  final Map<int, int> _eventVersions = {};
  final Map<int, NotificationItem> _readResults = {};
  final Set<int> _seenEvents = {}, _reading = {};

  Future<void> initialize() {
    if (_disposed) return Future<void>.value();
    _subscription ??= repository.events.listen(
      _onEvent,
      onError: (Object _) {
        if (_disposed) return;
        error = 'Không thể nhận sự kiện thông báo. Hãy tải lại.';
        notifyListeners();
      },
    );
    return _initializing ??= refresh();
  }

  Future<void> refresh() async {
    if (_disposed) return;
    final request = ++_pageRequest;
    final eventRevision = _eventRevision;
    loading = true;
    loadingMore = false;
    error = null;
    notifyListeners();
    try {
      final page = await repository.fetch();
      if (_disposed || request != _pageRequest) return;
      final arrivals = _items.where(
        (e) => (_eventVersions[e.id] ?? 0) > eventRevision,
      );
      _items = _merge([...page.items, ...arrivals]);
      _cursor = page.nextCursor;
      await _syncCount();
    } catch (_) {
      if (!_disposed && request == _pageRequest) {
        error = 'Không thể tải thông báo. Hãy thử lại.';
      }
    } finally {
      if (!_disposed && request == _pageRequest) {
        loading = false;
        notifyListeners();
      }
    }
  }

  Future<void> loadMore() async {
    if (_disposed || loading || loadingMore || _cursor == null) return;
    final request = _pageRequest, cursor = _cursor;
    loadingMore = true;
    error = null;
    notifyListeners();
    try {
      final page = await repository.fetch(cursor: cursor);
      if (_disposed || request != _pageRequest) return;
      _items = _merge([..._items, ...page.items]);
      _cursor = page.nextCursor;
    } catch (_) {
      if (!_disposed && request == _pageRequest) {
        error = 'Không thể tải thêm thông báo. Hãy thử lại.';
      }
    } finally {
      if (!_disposed && request == _pageRequest) {
        loadingMore = false;
        notifyListeners();
      }
    }
  }

  List<NotificationItem> _merge(Iterable<NotificationItem> candidates) {
    final old = {for (final item in _items) item.id: item};
    final merged = <int, NotificationItem>{};
    for (final item in candidates) {
      if (item.userId != accountId) continue;
      final previous = _readResults[item.id] ?? merged[item.id] ?? old[item.id];
      merged[item.id] = previous?.read == true && !item.read
          ? item.withRead(previous!.readAt ?? DateTime.now().toUtc())
          : item;
    }
    return merged.values.toList()..sort((a, b) {
      final time = b.createdAt.compareTo(a.createdAt);
      return time == 0 ? b.id.compareTo(a.id) : time;
    });
  }

  Future<void> _syncCount() {
    if (_disposed) return Future<void>.value();
    _countRequest++;
    _countDirty = true;
    return _countFuture ??= _drainCount();
  }

  Future<void> _drainCount() async {
    try {
      while (!_disposed && _countDirty) {
        _countDirty = false;
        final request = _countRequest;
        try {
          final count = await repository.fetchUnreadCount();
          if (_disposed) return;
          if (request != _countRequest) continue;
          if (count < 0) throw const FormatException('Invalid unread count');
          unreadCount = count;
          notifyListeners();
        } catch (_) {
          if (!_disposed && request == _countRequest) {
            error = 'Không thể cập nhật số chưa đọc. Hãy thử lại.';
            notifyListeners();
          }
        }
      }
    } finally {
      _countFuture = null;
    }
  }

  Future<bool> markRead(NotificationItem item) {
    if (_disposed || item.userId != accountId) return Future<bool>.value(false);
    if (item.read) return Future<bool>.value(true);
    return markReadById(item.id);
  }

  /// Push taps can reference an owned record beyond the current inbox page.
  Future<bool> markReadById(int id) async {
    if (_disposed || id <= 0 || !_reading.add(id)) return false;
    error = null;
    try {
      final updated = await repository.markRead(id);
      if (_disposed) return false;
      if (updated.id != id || updated.userId != accountId || !updated.read) {
        throw const FormatException('Invalid mark-read response');
      }
      _readResults[id] = updated;
      _items = _merge(_items.map((e) => e.id == id ? updated : e));
      notifyListeners();
      await _syncCount();
      return !_disposed;
    } catch (_) {
      if (!_disposed) {
        error = 'Không thể đánh dấu đã đọc. Hãy thử lại.';
        notifyListeners();
        await _syncCount();
      }
      return false;
    } finally {
      _reading.remove(id);
    }
  }

  Future<bool> markAllRead() async {
    if (_disposed || markingAllRead) return false;
    final eventRevision = _eventRevision;
    markingAllRead = true;
    error = null;
    notifyListeners();
    try {
      await repository.markAllRead();
      if (_disposed) return false;
      // A page captured before this write may include unseen unread rows.
      // Fence it, retain the cursor, and let the next request read server state.
      _pageRequest++;
      loading = false;
      loadingMore = false;
      final now = DateTime.now().toUtc();
      bool predatesOperation(NotificationItem item) =>
          (_eventVersions[item.id] ?? 0) <= eventRevision;
      _items = _items
          .map((item) => predatesOperation(item) ? item.withRead(now) : item)
          .toList();
      for (final item in _items.where(predatesOperation)) {
        _readResults[item.id] = item;
      }
      await _syncCount();
      return !_disposed;
    } catch (_) {
      if (!_disposed) error = 'Không thể đánh dấu tất cả. Hãy thử lại.';
      return false;
    } finally {
      if (!_disposed) {
        markingAllRead = false;
        notifyListeners();
      }
    }
  }

  void _onEvent(NotificationItem item) {
    if (_disposed || item.userId != accountId || !_seenEvents.add(item.id)) {
      return;
    }
    _eventVersions[item.id] = ++_eventRevision;
    final known = _items.any((e) => e.id == item.id);
    _items = _merge([item, ..._items]);
    if (!known && !item.read) unreadCount++;
    foregroundNotification = item;
    notifyListeners();
    unawaited(_syncCount());
  }

  void dismissBanner() {
    if (_disposed) return;
    foregroundNotification = null;
    notifyListeners();
  }

  void setDemoOnline(bool value) {
    if (_disposed || repository is! DemoNotificationRepository) return;
    (repository as DemoNotificationRepository).online = value;
    notifyListeners();
  }

  Future<void> simulateForegroundEvent() async {
    if (_disposed || repository is! DemoNotificationRepository) return;
    try {
      await (repository as DemoNotificationRepository)
          .simulateForegroundEvent();
    } catch (_) {
      if (!_disposed) {
        error = 'Không thể tạo sự kiện demo. Hãy kết nối lại và thử lại.';
        notifyListeners();
      }
    }
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _pageRequest++;
    _countRequest++;
    unawaited(_subscription?.cancel());
    unawaited(repository.dispose());
    super.dispose();
  }
}
