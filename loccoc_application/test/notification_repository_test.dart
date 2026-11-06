import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/core/storage/post_local_store.dart';
import 'package:loccoc_application/features/notifications/notification_repository.dart';

class FailingStore extends PostLocalStore {
  bool failWrites = false;
  FailingStore({required super.accountId, required super.directory});
  @override
  Future<void> write(String key, Map<String, dynamic> value) async {
    if (failWrites) throw const FileSystemException('disk full');
    await super.write(key, value);
  }
}

void main() {
  late Directory directory;
  setUp(() async {
    directory = await Directory.systemTemp.createTemp('notification-test-');
  });
  tearDown(() async {
    await directory.delete(recursive: true);
  });
  test('demo read state and arrivals survive repository restart with stable readAt', () async {
    final store = PostLocalStore(accountId: 1, directory: directory.path);
    final repo = DemoNotificationRepository(accountId: 1, store: store);
    final first = (await repo.fetch()).items.first;
    final read = await repo.markRead(first.id);
    expect((await repo.markRead(first.id)).readAt, read.readAt);
    final arrival = await repo.simulateForegroundEvent();
    final restored = DemoNotificationRepository(accountId: 1, store: store);
    final page = await restored.fetch();
    expect(page.items.first.id, arrival.id);
    expect(page.items.firstWhere((e) => e.id == first.id).readAt, read.readAt);
    expect(await restored.fetchUnreadCount(), await repo.fetchUnreadCount());
  });
  test(
    'failed durable mutation neither marks read nor emits a foreground event',
    () async {
      final store = FailingStore(accountId: 1, directory: directory.path);
      final repo = DemoNotificationRepository(accountId: 1, store: store);
      final before = await repo.fetchUnreadCount();
      final events = <int>[];
      final subscription = repo.events.listen((item) => events.add(item.id));
      store.failWrites = true;
      await expectLater(
        repo.markAllRead(),
        throwsA(isA<FileSystemException>()),
      );
      await expectLater(
        repo.simulateForegroundEvent(),
        throwsA(isA<FileSystemException>()),
      );
      expect(await repo.fetchUnreadCount(), before);
      expect(events, isEmpty);
      store.failWrites = false;
      await repo.markAllRead();
      expect(await repo.fetchUnreadCount(), 0);
      await subscription.cancel();
      await repo.dispose();
    },
  );
  test(
    'account storage is isolated and injection cannot cross accounts',
    () async {
      final one = DemoNotificationRepository(
        accountId: 1,
        store: PostLocalStore(accountId: 1, directory: directory.path),
      );
      final two = DemoNotificationRepository(
        accountId: 2,
        store: PostLocalStore(accountId: 2, directory: directory.path),
      );
      await one.markAllRead();
      expect(await one.fetchUnreadCount(), 0);
      expect(await two.fetchUnreadCount(), greaterThan(0));
      expect(
        () => DemoNotificationRepository(
          accountId: 2,
          store: PostLocalStore(accountId: 1),
        ),
        throwsArgumentError,
      );
    },
  );
  test('pagination exhausts long demo inbox without duplicate IDs and offline retry is honest', () async {
    final repo = DemoNotificationRepository(
      accountId: 1,
      store: PostLocalStore(accountId: 1, directory: directory.path),
    );
    final ids = <int>{};
    String? cursor;
    do {
      final page = await repo.fetch(cursor: cursor, limit: 7);
      ids.addAll(page.items.map((e) => e.id));
      cursor = page.nextCursor;
    } while (cursor != null);
    expect(ids.length, greaterThan(40));
    repo.online = false;
    await expectLater(repo.markAllRead(), throwsStateError);
    repo.online = true;
    expect(await repo.fetchUnreadCount(), greaterThan(0));
  });
}
