import 'dart:async';

import 'package:flutter/material.dart';
import 'package:loccoc_application/core/storage/post_local_store.dart';
import 'package:loccoc_application/features/notifications/notification_repository.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/notifications/notification_center_screen.dart';
import 'package:loccoc_application/features/notifications/notification_controller.dart';
import 'package:loccoc_application/features/notifications/notification_permission.dart';

import 'notification_controller_test.dart' show TestRepository;

class PermissionSpy implements NotificationPermissionAdapter {
  int calls = 0;
  @override
  Future<NotificationPermissionResult> request() async {
    calls++;
    return NotificationPermissionResult.denied;
  }
}

void main() {
  testWidgets(
    'inbox opens typed target despite read failure and exposes retry',
    (tester) async {
      final repo = TestRepository()..failRead = true;
      final controller = NotificationController(repository: repo, accountId: 1);
      await controller.initialize();
      NotificationTarget? opened;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: NotificationCenterScreen(
              controller: controller,
              onOpen: (target) async {
                opened = target;
              },
            ),
          ),
        ),
      );
      expect(find.text('3 chưa đọc'), findsOneWidget);
      await tester.tap(find.text('Post 3'));
      await tester.pumpAndSettle();
      expect(opened?.id, 3);
      expect(find.textContaining('Không thể'), findsWidgets);
      expect(find.text('Thử lại'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      controller.dispose();
      await repo.channel.close();
    },
  );
  testWidgets(
    'OS request follows explanation confirmation and cancel does nothing',
    (tester) async {
      final repo = TestRepository();
      final controller = NotificationController(repository: repo, accountId: 1);
      final permission = PermissionSpy();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: NotificationCenterScreen(
              controller: controller,
              onOpen: (_) async {},
              permissionAdapter: permission,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(permission.calls, 0);
      await tester.tap(find.text('Cài đặt thông báo'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Thông báo đẩy giúp'), findsOneWidget);
      expect(permission.calls, 0);
      await tester.tap(find.text('Để sau'));
      await tester.pumpAndSettle();
      expect(permission.calls, 0);
      await tester.tap(find.text('Cài đặt thông báo'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Tiếp tục'));
      await tester.pumpAndSettle();
      expect(permission.calls, 1);
      expect(find.textContaining('đã từ chối'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      controller.dispose();
      await repo.channel.close();
    },
  );
  testWidgets('demo action emits banner and opening it uses typed chat ID', (
    tester,
  ) async {
    final repo = DemoNotificationRepository(
      accountId: 1,
      store: MemoryNotificationStore(),
    );
    final controller = NotificationController(repository: repo, accountId: 1);
    NotificationTarget? target;
    Future<void> open(NotificationTarget value) async {
      target = value;
    }

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Column(
            children: [
              NotificationForegroundBanner(
                controller: controller,
                onOpen: open,
              ),
              Expanded(
                child: NotificationCenterScreen(
                  controller: controller,
                  onOpen: open,
                ),
              ),
            ],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('DEMO • dữ liệu mô phỏng'), findsOneWidget);
    await tester.tap(find.text('Tạo thông báo demo'));
    await tester.pumpAndSettle();
    expect(controller.foregroundNotification, isNotNull);
    expect(find.byType(MaterialBanner), findsOneWidget);
    expect(controller.foregroundNotification!.read, false);
    await tester.tap(find.text('Mở'));
    await tester.pumpAndSettle();
    expect(target?.kind, NotificationTargetKind.conversation);
    expect(target?.id, 10);
    expect(target?.messageId, 99);
    await tester.pumpWidget(const SizedBox());
    controller.dispose();
    await tester.pumpAndSettle();
  });
  testWidgets('pending permission does not update after screen disposal', (
    tester,
  ) async {
    final repo = TestRepository();
    final controller = NotificationController(repository: repo, accountId: 1);
    final permission = PendingPermission();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: NotificationCenterScreen(
            controller: controller,
            onOpen: (_) async {},
            permissionAdapter: permission,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cài đặt thông báo'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Tiếp tục'));
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox());
    permission.result.complete(NotificationPermissionResult.granted);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    controller.dispose();
    await repo.channel.close();
  });
  test('demo permission result never claims real OS permission', () async {
    expect(
      await DemoNotificationPermissionAdapter().request(),
      NotificationPermissionResult.simulated,
    );
  });
}

class PendingPermission implements NotificationPermissionAdapter {
  final result = Completer<NotificationPermissionResult>();
  @override
  Future<NotificationPermissionResult> request() => result.future;
}

class MemoryNotificationStore extends PostLocalStore {
  Map<String, dynamic>? value;
  MemoryNotificationStore() : super(accountId: 1);
  @override
  Future<Map<String, dynamic>?> read(String key) async => value;
  @override
  Future<void> write(String key, Map<String, dynamic> next) async {
    value = next;
  }
}
