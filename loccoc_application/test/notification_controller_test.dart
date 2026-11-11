import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/notifications/notification_controller.dart';
import 'package:loccoc_application/features/notifications/notification_models.dart';
import 'package:loccoc_application/features/notifications/notification_repository.dart';

NotificationItem item(int id, {bool read = false, int userId = 1}) =>
    NotificationItem(
      id: id,
      userId: userId,
      type: 'NEW_POST',
      title: 'Post $id',
      body: 'Body',
      read: read,
      createdAt: DateTime.utc(2026, 11, 6, 0, id),
      target: NotificationTarget.parse(
        type: 'NEW_POST',
        target: {'kind': 'POST', 'id': id},
      ),
    );

class TestRepository implements NotificationRepository {
  @override
  final int accountId = 1;
  final channel = StreamController<NotificationItem>.broadcast(sync: true);
  List<NotificationItem> rows = [item(3), item(2), item(1)];
  bool failRead = false, failFetch = false;
  int disposed = 0;
  Completer<void>? readAllGate;
  Completer<NotificationPage>? pageGate;
  Completer<int>? countGate;
  final List<String?> cursors = [];
  @override
  Stream<NotificationItem> get events => channel.stream;
  @override
  Future<NotificationPage> fetch({String? cursor, int limit = 20}) async {
    cursors.add(cursor);
    if (failFetch) throw StateError('offline');
    if (pageGate != null) return pageGate!.future;
    return cursor == null
        ? NotificationPage(items: rows.take(2).toList(), nextCursor: 'older')
        : NotificationPage(items: rows.skip(1).toList());
  }

  @override
  Future<int> fetchUnreadCount() async =>
      countGate == null ? rows.where((e) => !e.read).length : countGate!.future;
  @override
  Future<NotificationItem> markRead(int id) async {
    if (failRead) throw StateError('offline');
    final index = rows.indexWhere((e) => e.id == id);
    rows[index] = rows[index].withRead(DateTime.utc(2026));
    return rows[index];
  }

  @override
  Future<void> markAllRead() async {
    final ids = rows.map((e) => e.id).toSet();
    await readAllGate?.future;
    rows = rows
        .map((e) => ids.contains(e.id) ? e.withRead(DateTime.utc(2026)) : e)
        .toList();
  }

  @override
  Future<void> dispose() async {
    disposed++;
  }

  void arrive(NotificationItem value) {
    rows.insert(0, value);
    channel.add(value);
  }
}

Future<void> settle() async {
  for (var i = 0; i < 10; i++) {
    await Future<void>.delayed(Duration.zero);
  }
}

void main() {
  late TestRepository repo;
  late NotificationController controller;
  setUp(() {
    repo = TestRepository();
    controller = NotificationController(repository: repo, accountId: 1);
  });
  tearDown(() async {
    controller.dispose();
    await repo.channel.close();
  });
  test(
    'cursor pagination merges overlap once and authoritative unread count',
    () async {
      await controller.initialize();
      expect(controller.items.map((e) => e.id), [3, 2]);
      expect(controller.unreadCount, 3);
      await controller.loadMore();
      expect(controller.items.map((e) => e.id), [3, 2, 1]);
      expect(repo.cursors, [null, 'older']);
      expect(controller.hasMore, false);
    },
  );
  test(
    'late first page preserves cold-start read for previously unloaded ID',
    () async {
      await controller.initialize();
      final stale = repo.rows.last;
      repo.pageGate = Completer<NotificationPage>();
      final refresh = controller.refresh();
      await controller.markReadById(1);
      repo.pageGate!.complete(NotificationPage(items: [stale]));
      await refresh;
      expect(controller.items.single.id, 1);
      expect(controller.items.single.read, true);
    },
  );
  test('failed next page retains cursor for retry', () async {
    await controller.initialize();
    repo.failFetch = true;
    await controller.loadMore();
    expect(controller.error, isNotNull);
    expect(controller.items.map((e) => e.id), [3, 2]);
    expect(controller.hasMore, true);
    repo.failFetch = false;
    await controller.loadMore();
    expect(repo.cursors, [null, 'older', 'older']);
    expect(controller.items.map((e) => e.id), [3, 2, 1]);
  });
  test(
    'cold-start can mark an owned notification not loaded in first page',
    () async {
      await controller.initialize();
      expect(controller.items.any((item) => item.id == 1), false);
      expect(await controller.markReadById(1), true);
      expect(controller.unreadCount, 2);
      expect(repo.rows.firstWhere((item) => item.id == 1).read, true);
    },
  );
  test(
    'mark by ID rejects invalid IDs and contains repository errors',
    () async {
      await controller.initialize();
      expect(await controller.markReadById(0), false);
      repo.failRead = true;
      expect(await controller.markReadById(1), false);
      expect(controller.error, isNotNull);
      expect(controller.unreadCount, 3);
    },
  );
  test(
    'read-all fences pre-read older page and permits authoritative retry',
    () async {
      await controller.initialize();
      final stale = repo.rows.last;
      repo.pageGate = Completer<NotificationPage>();
      final older = controller.loadMore();
      await controller.markAllRead();
      repo.pageGate!.complete(NotificationPage(items: [stale]));
      await older;
      expect(controller.unreadCount, 0);
      expect(controller.items.every((item) => item.read), true);
      expect(controller.loadingMore, false);
      repo.pageGate = null;
      await controller.loadMore();
      expect(controller.items.map((item) => item.id), [3, 2, 1]);
      expect(controller.items.every((item) => item.read), true);
    },
  );

  test('read-all fences an older first-page refresh', () async {
    await controller.initialize();
    final stale = repo.rows.toList();
    repo.pageGate = Completer<NotificationPage>();
    final refreshing = controller.refresh();
    await controller.markAllRead();
    repo.pageGate!.complete(NotificationPage(items: stale));
    await refreshing;
    expect(controller.unreadCount, 0);
    expect(controller.items.every((item) => item.read), true);
    expect(controller.loading, false);
  });

  test('read failure keeps unread and allows explicit retry', () async {
    await controller.initialize();
    repo.failRead = true;
    expect(await controller.markRead(controller.items.first), false);
    expect(controller.items.first.read, false);
    expect(controller.error, isNotNull);
    repo.failRead = false;
    expect(await controller.markRead(controller.items.first), true);
    expect(controller.items.first.read, true);
    expect(controller.unreadCount, 2);
  });
  test(
    'read-all arrival race preserves new unread and reloads badge',
    () async {
      await controller.initialize();
      repo.readAllGate = Completer<void>();
      final operation = controller.markAllRead();
      repo.arrive(item(4));
      repo.readAllGate!.complete();
      await operation;
      await settle();
      expect(controller.unreadCount, 1);
      expect(controller.items.first.id, 4);
      expect(controller.items.first.read, false);
      expect(
        controller.items.where((e) => e.id != 4).every((e) => e.read),
        true,
      );
    },
  );
  test('stale count cannot erase an event arrival', () async {
    await controller.initialize();
    repo.countGate = Completer<int>();
    final operation = controller.markRead(controller.items.first);
    await settle();
    repo.arrive(item(4));
    final old = repo.countGate!;
    repo.countGate = null;
    old.complete(2);
    await operation;
    await settle();
    expect(controller.unreadCount, 3);
  });
  test(
    'foreground events deduplicate, do not mark read and ignore wrong account',
    () async {
      await controller.initialize();
      repo.arrive(item(4));
      repo.channel.add(item(4));
      repo.channel.add(item(8, userId: 2));
      await settle();
      expect(controller.items.where((e) => e.id == 4).length, 1);
      expect(controller.items.any((e) => e.id == 8), false);
      expect(controller.foregroundNotification?.id, 4);
      expect(controller.foregroundNotification?.read, false);
      controller.dismissBanner();
      expect(controller.foregroundNotification, isNull);
    },
  );
  test('refresh failure is recoverable', () async {
    repo.failFetch = true;
    await controller.initialize();
    expect(controller.error, isNotNull);
    repo.failFetch = false;
    await controller.refresh();
    expect(controller.items, isNotEmpty);
    expect(controller.error, isNull);
  });
  test(
    'late page cannot regress successful read or lose concurrent event',
    () async {
      await controller.initialize();
      final old = List<NotificationItem>.of(repo.rows.take(2));
      repo.pageGate = Completer<NotificationPage>();
      final refresh = controller.refresh();
      await controller.markRead(controller.items.first);
      repo.arrive(item(4));
      repo.pageGate!.complete(NotificationPage(items: old));
      await refresh;
      expect(controller.items.first.id, 4);
      expect(controller.items.firstWhere((e) => e.id == 3).read, true);
    },
  );
  test('dispose fences delayed initialization and events', () async {
    repo.pageGate = Completer<NotificationPage>();
    final operation = controller.initialize();
    var changes = 0;
    controller.addListener(() => changes++);
    controller.dispose();
    repo.pageGate!.complete(NotificationPage(items: [item(3)]));
    await operation;
    repo.channel.add(item(4));
    await settle();
    expect(changes, 0);
    expect(controller.items, isEmpty);
    expect(repo.disposed, 1);
  });
}
