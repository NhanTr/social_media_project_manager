import 'dart:async';

import '../../core/storage/post_local_store.dart';
import 'notification_models.dart';

abstract interface class NotificationRepository {
  int get accountId;
  Stream<NotificationItem> get events;
  Future<NotificationPage> fetch({String? cursor, int limit = 20});
  Future<int> fetchUnreadCount();
  Future<NotificationItem> markRead(int id);
  Future<void> markAllRead();
  Future<void> dispose();
}

/// A local, durable simulated server. It never claims to be an API/push provider.
class DemoNotificationRepository implements NotificationRepository {
  @override
  final int accountId;
  final PostLocalStore store;
  bool online = true;
  final _events = StreamController<NotificationItem>.broadcast(sync: true);
  Future<void> _pending = Future<void>.value();
  List<NotificationItem>? _rows;
  DemoNotificationRepository({required this.accountId, PostLocalStore? store})
    : store =
          store ??
          PostLocalStore(
            accountId: accountId,
            namespace: 's3-notifications-demo',
          ) {
    if (accountId <= 0 || this.store.accountId != accountId) {
      throw ArgumentError(
        'Notification store must belong to the current account',
      );
    }
  }
  @override
  Stream<NotificationItem> get events => _events.stream;

  Future<T> _run<T>(Future<T> Function() action) {
    final result = _pending.then((_) async {
      if (!online) throw StateError('Demo đang mất mạng');
      if (_rows == null) {
        final saved = await store.read('notification_inbox');
        if (saved == null) {
          final seeded = _seed();
          await store.write('notification_inbox', {
            'items': seeded.map((e) => e.toJson()).toList(),
          });
          _rows = seeded;
        } else {
          final raw = saved['items'];
          if (raw is! List) {
            throw const FormatException('Invalid demo inbox snapshot');
          }
          final restored = raw
              .map(
                (e) => NotificationItem.fromJson(
                  Map<String, dynamic>.from(e as Map),
                ),
              )
              .toList();
          if (restored.any((e) => e.userId != accountId)) {
            throw const FormatException('Notification account mismatch');
          }
          _rows = restored;
        }
      }
      return action();
    });
    _pending = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return result;
  }

  Future<void> _save(List<NotificationItem> rows) async {
    await store.write('notification_inbox', {
      'items': rows.map((e) => e.toJson()).toList(),
    });
    _rows = rows;
  }

  @override
  Future<NotificationPage> fetch({String? cursor, int limit = 20}) => _run(
    () async {
      if (limit < 1 || limit > 50) throw ArgumentError.value(limit, 'limit');
      // Cursor encodes the stable last ID for this local demo, independent of arrivals.
      var start = 0;
      if (cursor != null) {
        if (!cursor.startsWith('demo-notification-')) {
          throw const FormatException('Invalid cursor');
        }
        final id = int.tryParse(cursor.substring('demo-notification-'.length));
        final position = _rows!.indexWhere((e) => e.id == id);
        if (position < 0) throw const FormatException('Invalid cursor');
        start = position + 1;
      }
      final page = _rows!.skip(start).take(limit).toList();
      return NotificationPage(
        items: page,
        nextCursor: start + page.length < _rows!.length
            ? 'demo-notification-${page.last.id}'
            : null,
      );
    },
  );
  @override
  Future<int> fetchUnreadCount() =>
      _run(() async => _rows!.where((e) => !e.read).length);
  @override
  Future<NotificationItem> markRead(int id) => _run(() async {
    final index = _rows!.indexWhere((e) => e.id == id);
    if (index < 0) throw StateError('Nội dung không còn khả dụng');
    final rows = List<NotificationItem>.of(_rows!);
    rows[index] = rows[index].withRead(DateTime.now().toUtc());
    await _save(rows);
    return rows[index];
  });
  @override
  Future<void> markAllRead() => _run(() async {
    final now = DateTime.now().toUtc();
    await _save(_rows!.map((e) => e.withRead(now)).toList());
  });
  Future<NotificationItem> simulateForegroundEvent() => _run(() async {
    final latest =
        _rows!.fold<int>(
          0,
          (value, item) => item.id > value ? item.id : value,
        ) +
        1;
    final item = NotificationItem(
      id: latest,
      userId: accountId,
      type: 'CHAT_MESSAGE',
      title: 'Tin nhắn demo mới',
      body: 'Minh vừa gửi tin nhắn trong cuộc trò chuyện mẫu.',
      createdAt: DateTime.now().toUtc(),
      referenceId: 99,
      target: NotificationTarget.parse(
        type: 'CHAT_MESSAGE',
        target: {'kind': 'CONVERSATION', 'id': 10, 'messageId': 99},
      ),
    );
    await _save([item, ..._rows!]);
    _events.add(item);
    return item;
  });
  @override
  Future<void> dispose() async {
    await _pending;
    await _events.close();
  }

  List<NotificationItem> _seed() {
    final targets = [
      ('FRIEND_REQUEST', 'FRIEND_REQUEST', 201, 'Lời mời kết bạn demo'),
      ('FRIEND_ACCEPT', 'PROFILE', 2, 'Bạn bè mới demo'),
      ('NEW_POST', 'POST', 301, 'Bài đăng demo'),
      ('CHAT_MESSAGE', 'CONVERSATION', 10, 'Tin nhắn demo'),
      ('SUBSCRIPTION_UPDATED', 'SUBSCRIPTION', 401, 'Gói đăng ký demo'),
      ('REPORT_RESULT', 'REPORT', 501, 'Kết quả báo cáo demo'),
      ('UNKNOWN', 'UNKNOWN', 1, 'Nội dung không còn khả dụng'),
    ];
    return List.generate(47, (index) {
      final data = targets[index % targets.length];
      return NotificationItem(
        id: 1000 - index,
        userId: accountId,
        type: data.$1,
        title: index < 7 ? data.$4 : '${data.$4} ${index + 1}',
        body: switch (data.$1) {
          'FRIEND_REQUEST' => 'Minh muốn kết nối với bạn.',
          'FRIEND_ACCEPT' => 'Minh đã chấp nhận lời mời kết bạn.',
          'NEW_POST' => 'Minh vừa chia sẻ một khoảnh khắc mới.',
          'CHAT_MESSAGE' => 'Hẹn bạn ở thư viện nhé!',
          'SUBSCRIPTION_UPDATED' =>
            'Thông tin gói đăng ký mẫu đã được cập nhật.',
          'REPORT_RESULT' => 'Báo cáo mẫu của bạn đã được xem xét.',
          _ => 'Bạn có thể quay lại để xem các thông báo khác.',
        },
        createdAt: DateTime.utc(2026, 11, 6).subtract(Duration(minutes: index)),
        read: index >= 7,
        readAt: index >= 7 ? DateTime.utc(2026, 11, 6) : null,
        referenceId: data.$1 == 'CHAT_MESSAGE' ? 99 : data.$3,
        target: NotificationTarget.parse(
          type: data.$1,
          target: {
            'kind': data.$2,
            'id': data.$3,
            if (data.$1 == 'CHAT_MESSAGE') 'messageId': 99,
          },
        ),
      );
    });
  }
}
