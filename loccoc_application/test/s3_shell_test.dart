import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:loccoc_application/core/network/api_client.dart';
import 'package:loccoc_application/core/storage/post_local_store.dart';
import 'package:loccoc_application/features/chat/chat_controller.dart';
import 'package:loccoc_application/features/chat/demo_chat_repository.dart';
import 'package:loccoc_application/features/notifications/notification_controller.dart';
import 'package:loccoc_application/features/notifications/notification_repository.dart';
import 'package:loccoc_application/features/chat/chat_screen.dart';
import 'package:loccoc_application/features/notifications/notification_center_screen.dart';
import 'package:loccoc_application/features/s3/notification_launch_controller.dart';
import 'package:loccoc_application/features/s3/s3_session.dart';
import 'package:loccoc_application/features/shell/app_shell.dart';

import 'capture_to_feed_test.dart' show Auth, Session;

void main() {
  late S3Session session;
  late Auth auth;
  tearDown(() {
    auth.dispose();
    session.dispose();
  });
  Widget shell({NotificationLaunchController? launches}) {
    // Build all async domain objects inside the widget test's zone. Persistence
    // itself is covered with real disk by session/storage/repository unit tests.
    final chatStore = _MemoryStore();
    session = S3Session(
      accountId: 1,
      isDemo: true,
      chat: ChatController(
        accountId: 1,
        store: chatStore,
        repository: DemoChatRepository(accountId: 1, store: chatStore),
      ),
      notifications: NotificationController(
        accountId: 1,
        repository: DemoNotificationRepository(
          accountId: 1,
          store: _MemoryStore(),
        ),
      ),
    );
    auth = Auth(
      ApiClient(
        storage: Session(),
        client: MockClient((request) async {
          if (request.url.path == '/api/v1/feed') {
            return http.Response(
              '{"items":[],"hasNext":false,"nextCursor":null}',
              200,
            );
          }
          return http.Response('[]', 200);
        }),
      ),
    );
    return MaterialApp(
      navigatorObservers: [chatRouteObserver],
      home: AppShell(
        authController: auth,
        s3SessionFactory: (_) => session,
        notificationLaunches: launches,
      ),
    );
  }

  testWidgets('shell exposes durable demo chat and notification unread badge', (
    tester,
  ) async {
    await tester.pumpWidget(shell());
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('s3-unread-badge')), findsOneWidget);
    await tester.tap(find.byTooltip('Thông báo'));
    await tester.pumpAndSettle();
    expect(find.byType(NotificationCenterScreen), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Tin nhắn').last);
    await tester.pumpAndSettle();
    expect(find.byType(ConversationListScreen), findsOneWidget);
    expect(find.textContaining('đang được lên kế hoạch'), findsNothing);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets(
    'cold-start push opens conversation once, not the message ID as room',
    (tester) async {
      final launches = NotificationLaunchController(
        initialPayload: {
          'notificationId': '501',
          'type': 'CHAT_MESSAGE',
          'targetKind': 'CONVERSATION',
          'targetId': '10',
          'messageId': '99',
        },
      );
      await tester.pumpWidget(shell(launches: launches));
      await tester.pumpAndSettle();
      final room = tester.widget<ChatRoomScreen>(find.byType(ChatRoomScreen));
      expect(room.conversationId, 10);
      expect(room.focusMessageId, 99);
      await tester.pageBack();
      await tester.pumpAndSettle();
      auth.notifyListeners();
      await tester.pumpAndSettle();
      expect(find.byType(ChatRoomScreen), findsNothing);
      await tester.pumpWidget(const SizedBox());
      launches.dispose();
    },
  );

  testWidgets(
    'invalid or deleted target falls back instead of guessing a route',
    (tester) async {
      final launches = NotificationLaunchController(
        initialPayload: {
          'notificationId': '999',
          'type': 'NEW_POST',
          'targetKind': 'POST',
          'targetId': '99999',
        },
      );
      await tester.pumpWidget(shell(launches: launches));
      await tester.pumpAndSettle();
      expect(find.text('Nội dung không còn khả dụng'), findsOneWidget);
      expect(find.byType(ChatRoomScreen), findsNothing);
      await tester.pumpWidget(const SizedBox());
      launches.dispose();
    },
  );
  testWidgets(
    'each supported non-chat target opens its typed preview destination',
    (tester) async {
      final launches = NotificationLaunchController();
      await tester.pumpWidget(shell(launches: launches));
      await tester.pumpAndSettle();
      const cases = [
        ('FRIEND_REQUEST', 'FRIEND_REQUEST', '201', 'Lời mời kết bạn'),
        ('FRIEND_ACCEPT', 'PROFILE', '2', 'Hồ sơ bạn bè'),
        ('NEW_POST', 'POST', '301', 'Chi tiết ảnh'),
        ('SUBSCRIPTION_UPDATED', 'SUBSCRIPTION', '401', 'Gói đăng ký'),
        ('REPORT_RESULT', 'REPORT', '501', 'Kết quả báo cáo'),
      ];
      for (var index = 0; index < cases.length; index++) {
        final value = cases[index];
        launches.offer({
          'notificationId': '${2000 + index}',
          'type': value.$1,
          'targetKind': value.$2,
          'targetId': value.$3,
        });
        await tester.pumpAndSettle();
        expect(find.text(value.$4), findsWidgets);
        await tester.pageBack();
        await tester.pumpAndSettle();
      }
      await tester.pumpWidget(const SizedBox());
      launches.dispose();
    },
  );
  testWidgets('foreground banner stays visible above an open chat route', (
    tester,
  ) async {
    final launches = NotificationLaunchController(
      initialPayload: {
        'notificationId': '997',
        'type': 'CHAT_MESSAGE',
        'targetKind': 'CONVERSATION',
        'targetId': '10',
        'messageId': '99',
      },
    );
    await tester.pumpWidget(shell(launches: launches));
    await tester.pumpAndSettle();
    expect(find.byType(ChatRoomScreen), findsOneWidget);
    await session.notifications.simulateForegroundEvent();
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('s3-foreground-banner')), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    launches.dispose();
  });
}

class _MemoryStore extends PostLocalStore {
  _MemoryStore() : super(accountId: 1);
  final _values = <String, Map<String, dynamic>>{};
  @override
  Future<Map<String, dynamic>?> read(String key) async => _values[key] == null
      ? null
      : jsonDecode(jsonEncode(_values[key])) as Map<String, dynamic>;
  @override
  Future<void> write(String key, Map<String, dynamic> value) async {
    _values[key] = jsonDecode(jsonEncode(value)) as Map<String, dynamic>;
  }
}
