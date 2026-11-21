import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/core/storage/post_local_store.dart';
import 'package:loccoc_application/features/chat/chat_controller.dart';
import 'package:loccoc_application/features/chat/chat_screen.dart';
import 'package:loccoc_application/features/chat/demo_chat_repository.dart';

import 'chat_controller_test.dart' show MemoryTransport, msg;

class WidgetStore extends PostLocalStore {
  WidgetStore() : super(accountId: 1);
  final values = <String, Map<String, dynamic>>{};
  @override
  Future<Map<String, dynamic>?> read(String key) async => values[key];
  @override
  Future<void> write(String key, Map<String, dynamic> value) async {
    values[key] = value;
  }
}

void main() {
  late MemoryTransport repo;
  late ChatController controller;
  setUp(() {
    repo = MemoryTransport();
  });
  tearDown(() async {
    controller.dispose();
    await repo.bus.close();
  });
  testWidgets(
    'conversation opens room and durable pending plus failure can retry',
    (tester) async {
      controller = ChatController(
        repository: repo,
        accountId: 1,
        store: WidgetStore(),
        delay: (_) async {},
      );
      await controller.initialize();
      await tester.pumpWidget(
        MaterialApp(
          navigatorObservers: [chatRouteObserver],
          home: ConversationListScreen(controller: controller, active: true),
        ),
      );
      expect(find.text('Bạn An'), findsOneWidget);
      await tester.tap(find.text('Bạn An'));
      await tester.pumpAndSettle();
      repo.terminal = true;
      await tester.enterText(find.byType(TextField), 'Thử gửi');
      await tester.tap(find.byTooltip('Gửi tin nhắn'));
      await controller.idle;
      await tester.pumpAndSettle();
      expect(find.text('Thử gửi'), findsOneWidget);
      expect(find.text('Gửi thất bại'), findsOneWidget);
      repo.terminal = false;
      await tester.tap(find.text('Thử lại'));
      await controller.idle;
      await tester.pumpAndSettle();
      expect(find.text('Đã gửi'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets('late ACK preserves the next draft typed while sending', (
    tester,
  ) async {
    controller = ChatController(
      repository: repo,
      accountId: 1,
      store: WidgetStore(),
      delay: (_) async {},
    );
    await controller.initialize();
    await tester.pumpWidget(
      MaterialApp(
        navigatorObservers: [chatRouteObserver],
        home: ChatRoomScreen(controller: controller, conversationId: 10),
      ),
    );
    await tester.pumpAndSettle();
    repo.gate = Completer<void>();
    await tester.enterText(find.byType(TextField), 'Tin đầu tiên');
    await tester.tap(find.byTooltip('Gửi tin nhắn'));
    await tester.pump();
    await tester.enterText(find.byType(TextField), 'Bản nháp tiếp theo');
    repo.gate!.complete();
    await controller.idle;
    await tester.pumpAndSettle();
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller!.text,
      'Bản nháp tiếp theo',
    );
    expect(
      controller
          .messagesFor(10)
          .where((m) => m.content == 'Tin đầu tiên')
          .single
          .id,
      isNotNull,
    );
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets(
    'only visible incoming rows produce READ; covered route stops it',
    (tester) async {
      controller = ChatController(
        repository: repo,
        accountId: 1,
        store: WidgetStore(),
        delay: (_) async {},
      );
      await controller.initialize();
      repo.canonical.addAll(List.generate(100, (i) => msg(i + 1)));
      await controller.reconnect();
      while (controller.hasMore(10)) {
        await controller.loadOlder(10);
      }
      await tester.pumpWidget(
        MaterialApp(
          navigatorObservers: [chatRouteObserver],
          home: ChatRoomScreen(controller: controller, conversationId: 10),
        ),
      );
      await tester.pumpAndSettle();
      await controller.idle;
      expect(repo.reads, isNotEmpty);
      expect(repo.reads.last, 100);
      final before = repo.reads.length;
      final context = tester.element(find.byType(ChatRoomScreen));
      Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => const Scaffold(body: Text('Che phòng')),
        ),
      );
      await tester.pumpAndSettle();
      repo.bus.add(MessageEvent(msg(101)));
      await controller.idle;
      await tester.pumpAndSettle();
      expect(repo.reads.length, before);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('historical focus scrolls to the actual target before READ', (
    tester,
  ) async {
    controller = ChatController(
      repository: repo,
      accountId: 1,
      store: WidgetStore(),
      delay: (_) async {},
    );
    repo.canonical.addAll(List.generate(100, (i) => msg(i + 1)));
    await controller.initialize();
    await tester.pumpWidget(
      MaterialApp(
        navigatorObservers: [chatRouteObserver],
        home: ChatRoomScreen(
          controller: controller,
          conversationId: 10,
          focusMessageId: 1,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await controller.idle;
    expect(find.text('Tin 1'), findsOneWidget);
    expect(repo.reads, isNotEmpty);
    expect(repo.reads.last, lessThan(100));
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('2000-message room mounts bounded rows and scrolls history', (
    tester,
  ) async {
    final storage = WidgetStore();
    final history = List.generate(2000, (i) => msg(i + 1));
    repo.canonical.addAll(history);
    await storage.write('chat_client', {
      'messages': history.map((m) => m.toJson()).toList(),
      'conversations': [
        {'id': 10, 'title': 'Bạn An'},
      ],
    });
    controller = ChatController(
      repository: repo,
      accountId: 1,
      store: storage,
      delay: (_) async {},
    );
    await controller.initialize();
    await tester.pumpWidget(
      MaterialApp(
        navigatorObservers: [chatRouteObserver],
        home: ChatRoomScreen(controller: controller, conversationId: 10),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Tin 2000'), findsOneWidget);
    expect(
      find.textContaining(RegExp(r'^Tin \d+$')).evaluate().length,
      lessThan(100),
    );
    await tester.drag(find.byType(ListView), const Offset(0, 600));
    await tester.pumpAndSettle();
    expect(find.text('Tin 2000'), findsNothing);
    expect(
      find.textContaining(RegExp(r'^Tin \d+$')).evaluate().length,
      lessThan(100),
    );
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets(
    'demo offline toggle exposes persisted pending then reconnect sends',
    (tester) async {
      final storage = WidgetStore();
      final demo = DemoChatRepository(accountId: 1, store: storage);
      controller = ChatController(
        repository: demo,
        accountId: 1,
        store: storage,
        delay: (_) async {},
      );
      await controller.initialize();
      await tester.pumpWidget(
        MaterialApp(
          navigatorObservers: [chatRouteObserver],
          home: ChatRoomScreen(controller: controller, conversationId: 10),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('MÔ PHỎNG'), findsOneWidget);
      await tester.tap(find.byTooltip('Mất mạng mô phỏng'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'Đợi mạng');
      await tester.tap(find.byTooltip('Gửi tin nhắn'));
      await tester.pumpAndSettle();
      expect(find.text('Đang chờ gửi'), findsOneWidget);
      await tester.tap(find.byTooltip('Kết nối lại mô phỏng'));
      await tester.pumpAndSettle();
      expect(find.text('Đang chờ gửi'), findsNothing);
      expect(find.text('Đợi mạng'), findsOneWidget);
      expect(
        controller
            .messagesFor(10)
            .where((m) => m.content == 'Đợi mạng')
            .single
            .id,
        isNotNull,
      );
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets('dispose cancels a scheduled reconnect backoff', (tester) async {
    controller = ChatController(
      repository: repo,
      accountId: 1,
      store: WidgetStore(),
    );
    await controller.initialize();
    repo.bus.add(const ConnectionLost());
    controller.dispose();
    await tester.pump();
  });
  testWidgets('unknown conversation has safe unavailable fallback', (
    tester,
  ) async {
    controller = ChatController(
      repository: repo,
      accountId: 1,
      store: WidgetStore(),
      delay: (_) async {},
    );
    await controller.initialize();
    await tester.pumpWidget(
      MaterialApp(
        navigatorObservers: [chatRouteObserver],
        home: ChatRoomScreen(controller: controller, conversationId: 999),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('không khả dụng'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });
}
