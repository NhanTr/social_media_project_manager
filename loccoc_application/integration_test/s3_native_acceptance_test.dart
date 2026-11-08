import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:loccoc_application/features/chat/chat_controller.dart';
import 'package:loccoc_application/features/chat/chat_screen.dart';
import 'package:loccoc_application/features/s3/s3_session.dart';
import 'package:loccoc_application/s3_demo.dart';

Future<void> pumpUntil(
  WidgetTester tester,
  bool Function() ready,
  String reason,
) async {
  final watch = Stopwatch()..start();
  while (!ready() && watch.elapsed < const Duration(seconds: 30)) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(ready(), isTrue, reason: reason);
  await tester.pumpAndSettle();
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('native S3: cold-start route, durable offline replay and inbox', (
    tester,
  ) async {
    // Real native filesystem, isolated from any normal demo/account data.
    final directory = await Directory.systemTemp.createTemp('loccoc-s3-native');
    S3Session session() => S3Session.forEnvironment(
      accountId: 1,
      environment: 'dev',
      directory: directory.path,
    )!;
    var current = session();
    try {
      await current.initialize();
      await tester.pumpWidget(
        S3DemoApp(
          sessionFactory: () => current,
          initialPayload: const {
            'notificationId': '997',
            'type': 'CHAT_MESSAGE',
            'targetKind': 'CONVERSATION',
            'targetId': '10',
            'messageId': '99',
          },
        ),
      );
      await pumpUntil(
        tester,
        () => find.byType(ChatRoomScreen).evaluate().isNotEmpty,
        'Injected cold-start payload should open the conversation route',
      );
      final room = tester.widget<ChatRoomScreen>(find.byType(ChatRoomScreen));
      expect(room.conversationId, 10);
      expect(room.focusMessageId, 99);

      await tester.tap(find.byTooltip('Mất mạng mô phỏng'));
      await pumpUntil(
        tester,
        () => current.chat.connection == ChatConnection.offline,
        'Offline control should disconnect the demo transport',
      );
      const content = 'Nghiệm thu native: giữ tin qua khởi tạo lại phiên';
      await tester.enterText(find.byType(TextField), content);
      await tester.tap(find.byTooltip('Gửi tin nhắn'));
      await pumpUntil(
        tester,
        () => current.chat
            .messagesFor(10)
            .any(
              (m) => m.content == content && m.status == MessageStatus.pending,
            ),
        'The message must be durably queued while offline',
      );
      expect(find.text('Đang chờ gửi'), findsOneWidget);
      final key = current.chat
          .messagesFor(10)
          .singleWhere((m) => m.content == content)
          .clientMessageId;
      await current.chat.idle;

      // Recreate controllers/repositories against the same native disk snapshot.
      // This exercises persistence without claiming an OS process-kill test.
      await tester.pumpWidget(const SizedBox());
      current.dispose();
      current = session();
      await current.initialize();
      await current.chat.reconnect();
      await current.chat.reconnect();
      final restored = current.chat
          .messagesFor(10)
          .where((m) => m.content == content)
          .toList();
      expect(restored, hasLength(1));
      expect(restored.single.clientMessageId, key);
      expect(restored.single.id, isNotNull);
      expect(restored.single.status, MessageStatus.sent);

      await tester.pumpWidget(S3DemoApp(sessionFactory: () => current));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Thông báo').last);
      await tester.pumpAndSettle();
      final beforeRead = current.notifications.unreadCount;
      await tester.tap(find.byKey(const ValueKey('notification-998')));
      await pumpUntil(
        tester,
        () =>
            find.text('Chi tiết ảnh').evaluate().isNotEmpty &&
            current.notifications.unreadCount == beforeRead - 1,
        'Opening a post notification should navigate and synchronize unread count',
      );
      await tester.pageBack();
      await tester.pumpAndSettle();

      await tester.tap(find.text('Tạo thông báo demo'));
      await pumpUntil(
        tester,
        () =>
            find.byKey(const Key('s3-foreground-banner')).evaluate().isNotEmpty,
        'A foreground event should show a banner',
      );
      await tester.tap(find.text('Mở'));
      await pumpUntil(
        tester,
        () => find.byType(ChatRoomScreen).evaluate().isNotEmpty,
        'The foreground banner must open its typed chat target',
      );
      expect(
        tester
            .widget<ChatRoomScreen>(find.byType(ChatRoomScreen))
            .conversationId,
        10,
      );
      await tester.pageBack();
      await tester.pumpAndSettle();

      final inboxScroll = find
          .descendant(
            of: find.byKey(const Key('notification-inbox')),
            matching: find.byType(Scrollable),
          )
          .first;
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('notification-994')),
        200,
        scrollable: inboxScroll,
      );
      await tester.tap(find.byKey(const ValueKey('notification-994')));
      await pumpUntil(
        tester,
        () => find.text('Nội dung không còn khả dụng').evaluate().isNotEmpty,
        'Unknown or missing targets must show the safe fallback',
      );
      await tester.pageBack();
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cài đặt thông báo'));
      await tester.pumpAndSettle();
      expect(find.text('Cho phép thông báo?'), findsOneWidget);
      await tester.tap(find.text('Để sau'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cài đặt thông báo'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Tiếp tục'));
      await pumpUntil(
        tester,
        () => find.textContaining('Đã mô phỏng lựa chọn').evaluate().isNotEmpty,
        'Permission simulation must follow the explanation and stay honest about OS permission',
      );
      expect(tester.takeException(), isNull);
    } finally {
      await current.chat.idle;
      await tester.pumpWidget(const SizedBox());
      current.dispose();
      if (await directory.exists()) await directory.delete(recursive: true);
    }
  });
}
