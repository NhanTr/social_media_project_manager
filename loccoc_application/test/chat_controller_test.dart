import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/core/storage/post_local_store.dart';
import 'package:loccoc_application/features/chat/chat_controller.dart';

ChatMessage msg(
  int id, {
  int sender = 2,
  MessageStatus status = MessageStatus.sent,
}) => ChatMessage(
  id: id,
  conversationId: 10,
  senderId: sender,
  clientMessageId: 'key-$id',
  content: 'Tin $id',
  sentAt: DateTime.utc(2026, 1, 1).add(Duration(seconds: id)),
  status: status,
);

class MemoryTransport extends ChatRepository {
  @override
  final int accountId;
  MemoryTransport({this.accountId = 1});
  final bus = StreamController<ChatEvent>.broadcast(sync: true);
  final canonical = <ChatMessage>[];
  final reads = <int>[];
  final deliveredIds = <int>[];
  bool offline = false, terminal = false, loseAck = false;
  int sends = 0,
      simultaneous = 0,
      maxSimultaneous = 0,
      connects = 0,
      disconnects = 0;
  Completer<void>? gate;
  void Function()? beforeSend;
  final cursors = <String?>[];
  @override
  Stream<ChatEvent> get events => bus.stream;
  @override
  Future<void> connect() async {
    connects++;
    if (offline) throw const ChatFailure('offline');
  }

  @override
  Future<void> disconnect() async {
    disconnects++;
  }

  @override
  Future<List<ChatConversation>> conversations() async => [
    const ChatConversation(10, 'Bạn An'),
  ];
  @override
  Future<MessagePage> history(int id, {String? cursor}) async {
    cursors.add(cursor);
    final sorted = canonical.toList()..sort((a, b) => compareMessages(b, a));
    final start = cursor == null ? 0 : int.parse(cursor);
    return MessagePage(
      sorted.skip(start).take(2).toList(),
      nextCursor: start + 2 < sorted.length ? '${start + 2}' : null,
    );
  }

  @override
  Future<ChatMessage> send(ChatMessage m) async {
    sends++;
    beforeSend?.call();
    simultaneous++;
    if (simultaneous > maxSimultaneous) maxSimultaneous = simultaneous;
    try {
      if (gate != null) await gate!.future;
      if (offline) throw const ChatFailure('offline');
      if (terminal) throw const ChatFailure('blocked', terminal: true);
      final existing = canonical.where(
        (x) => x.clientMessageId == m.clientMessageId,
      );
      final result = existing.isNotEmpty
          ? existing.first
          : m.copyWith(id: 100 + canonical.length, status: MessageStatus.sent);
      if (existing.isEmpty) canonical.add(result);
      if (loseAck) throw const ChatFailure('ACK lost');
      return result;
    } finally {
      simultaneous--;
    }
  }

  @override
  Future<void> delivered(int id, int lastMessageId) async {
    deliveredIds.add(lastMessageId);
  }

  @override
  Future<void> read(int id, int lastMessageId) async {
    reads.add(lastMessageId);
  }

  @override
  Future<void> setOffline(bool value) async {
    offline = value;
  }
}

class FailingStore extends PostLocalStore {
  bool fail = false, failRead = false;
  @override
  Future<Map<String, dynamic>?> read(String key) async {
    if (failRead) throw const FileSystemException('disk locked');
    return super.read(key);
  }

  FailingStore(String path) : super(accountId: 1, directory: path);
  @override
  Future<void> write(String key, Map<String, dynamic> value) async {
    if (fail) throw const FileSystemException('disk full');
    return super.write(key, value);
  }
}

void main() {
  late Directory dir;
  late MemoryTransport repo;
  late PostLocalStore store;
  late ChatController controller;
  ChatController make({PostLocalStore? storage}) => ChatController(
    repository: repo,
    accountId: 1,
    store: storage ?? store,
    delay: (_) async {},
  );
  setUp(() async {
    dir = await Directory.systemTemp.createTemp('chat-test');
    store = PostLocalStore(accountId: 1, directory: dir.path);
    repo = MemoryTransport();
    controller = make();
  });
  tearDown(() async {
    controller.dispose();
    await repo.bus.close();
    await dir.delete(recursive: true);
  });
  test(
    'offline pending survives restart and replays original key once',
    () async {
      repo.offline = true;
      await controller.initialize();
      await controller.sendText(10, 'Xin chào');
      expect(controller.messagesFor(10).single.status, MessageStatus.pending);
      final key = controller.messagesFor(10).single.clientMessageId;
      controller.dispose();
      repo.offline = false;
      controller = make();
      await controller.initialize();
      expect(controller.messagesFor(10).single.clientMessageId, key);
      expect(controller.messagesFor(10).single.status, MessageStatus.sent);
      expect(repo.canonical.length, 1);
    },
  );
  test(
    'lost ACK and simultaneous reconnect preserve key without duplicate sends',
    () async {
      await controller.initialize();
      repo.loseAck = true;
      await controller.sendText(10, 'Một lần');
      expect(repo.canonical.length, 1);
      final key = repo.canonical.single.clientMessageId;
      controller.dispose();
      repo.loseAck = false;
      controller = make();
      await Future.wait([
        controller.initialize(),
        controller.reconnect(),
        controller.reconnect(),
      ]);
      expect(controller.messagesFor(10).single.clientMessageId, key);
      expect(controller.messagesFor(10).single.id, isNotNull);
      expect(repo.canonical.length, 1);
      expect(repo.maxSimultaneous, 1);
    },
  );
  test(
    'history pagination and events merge once with monotonic receipts',
    () async {
      repo.canonical.addAll([
        msg(1, sender: 1),
        msg(2, sender: 1),
        msg(3, sender: 1),
        msg(4, sender: 1),
      ]);
      await controller.initialize();
      await controller.loadOlder(10);
      repo.bus.add(MessageEvent(msg(3, sender: 1)));
      repo.bus.add(
        const ReceiptEvent(
          conversationId: 10,
          participantId: 2,
          lastMessageId: 3,
          status: MessageStatus.read,
        ),
      );
      repo.bus.add(
        const ReceiptEvent(
          conversationId: 10,
          participantId: 2,
          lastMessageId: 3,
          status: MessageStatus.delivered,
        ),
      );
      await controller.idle;
      expect(controller.messagesFor(10).map((m) => m.id), [1, 2, 3, 4]);
      expect(
        controller.messagesFor(10).take(3).map((m) => m.status),
        everyElement(MessageStatus.read),
      );
      expect(controller.messagesFor(10).last.status, MessageStatus.sent);
    },
  );
  test('READ respects foreground route and actual visible watermark', () async {
    repo.canonical.addAll([msg(1), msg(2), msg(3), msg(4)]);
    await controller.initialize();
    await controller.reportVisible(10, {3});
    expect(repo.reads, isEmpty);
    controller.setRoomVisible(10);
    controller.setForeground(false);
    await controller.reportVisible(10, {3});
    expect(repo.reads, isEmpty);
    controller.setForeground(true);
    await controller.reconnect();
    await controller.reportVisible(10, {3});
    expect(repo.reads, [3]);
    await controller.reportVisible(10, {3});
    expect(repo.reads, [3]);
    controller.setRoomVisible(null);
    await controller.reportVisible(10, {4});
    expect(repo.reads, [3]);
  });
  test('storage failure prevents network send and phantom pending', () async {
    controller.dispose();
    final failing = FailingStore(dir.path);
    controller = make(storage: failing);
    await controller.initialize();
    failing.fail = true;
    await controller.sendText(10, 'Không mất');
    expect(repo.sends, 0);
    expect(controller.messagesFor(10), isEmpty);
    expect(controller.error, isNotNull);
  });
  test(
    'terminal failure requires explicit retry with the same identity',
    () async {
      await controller.initialize();
      repo.terminal = true;
      await controller.sendText(10, 'Thử lại');
      expect(controller.messagesFor(10).single.status, MessageStatus.failed);
      expect(repo.sends, 1);
      final key = controller.messagesFor(10).single.clientMessageId;
      repo.terminal = false;
      await controller.reconnect();
      expect(controller.messagesFor(10).single.status, MessageStatus.failed);
      await controller.retry(key);
      expect(controller.messagesFor(10).single.status, MessageStatus.sent);
      expect(controller.messagesFor(10).single.clientMessageId, key);
    },
  );

  test(
    'storage initialization failure can be retried without abandoning queue',
    () async {
      controller.dispose();
      final failing = FailingStore(dir.path)..failRead = true;
      controller = make(storage: failing);
      await controller.initialize();
      expect(controller.error, isNotNull);
      failing.failRead = false;
      await controller.reconnect();
      await controller.sendText(10, 'Phục hồi');
      expect(controller.messagesFor(10).single.status, MessageStatus.sent);
    },
  );

  test(
    'reconnect traverses every offline gap page until cached history joins',
    () async {
      repo.canonical.addAll([msg(1), msg(2)]);
      await controller.initialize();
      repo.canonical.addAll(List.generate(10, (i) => msg(i + 3)));
      await controller.reconnect();
      expect(
        controller.messagesFor(10).map((m) => m.id),
        List.generate(12, (i) => i + 1),
      );
    },
  );
  test('send persists before transport and overlapping reconnect never sends in parallel', () async {
    await controller.initialize();
    repo.gate = Completer<void>();
    final pending = controller.sendText(10, 'Bền vững');
    while (repo.sends == 0) {
      await Future<void>.delayed(const Duration(milliseconds: 1));
    }
    final snapshot = await store.read('chat_client');
    expect((snapshot!['messages'] as List).single['status'], 'pending');
    final reconnect = controller.reconnect();
    repo.gate!.complete();
    await Future.wait([pending, reconnect]);
    expect(controller.messagesFor(10).single.status, MessageStatus.sent);
    expect(repo.maxSimultaneous, 1);
    expect(repo.canonical.length, 1);
  });
  test(
    'dispose suppresses late ACK and account mismatch is rejected',
    () async {
      await controller.initialize();
      repo.gate = Completer<void>();
      final pending = controller.sendText(10, 'Chậm');
      while (repo.sends == 0) {
        await Future<void>.delayed(const Duration(milliseconds: 1));
      }
      controller.dispose();
      repo.gate!.complete();
      await pending;
      expect(controller.messagesFor(10).single.status, MessageStatus.pending);
      expect(
        () => ChatController(repository: repo, accountId: 2, store: store),
        throwsArgumentError,
      );
    },
  );

  test('history cursor stays retryable when cache persistence fails', () async {
    controller.dispose();
    final failing = FailingStore(dir.path);
    controller = make(storage: failing);
    repo.canonical.addAll([msg(1), msg(2), msg(3), msg(4)]);
    await controller.initialize();
    failing.fail = true;
    await controller.loadOlder(10);
    expect(controller.messagesFor(10).map((m) => m.id), [3, 4]);
    failing.fail = false;
    await controller.loadOlder(10);
    expect(controller.messagesFor(10).map((m) => m.id), [1, 2, 3, 4]);
  });
  test(
    'unpersistable FAILED retains queue without retrying terminal request',
    () async {
      controller.dispose();
      final failing = FailingStore(dir.path);
      controller = make(storage: failing);
      await controller.initialize();
      repo.terminal = true;
      repo.beforeSend = () {
        failing.fail = true;
        if (repo.sends > 1) throw StateError('terminal request replayed');
      };
      await controller.sendText(10, 'Không lặp');
      expect(repo.sends, 1);
      expect(controller.messagesFor(10).single.status, MessageStatus.pending);
      expect(controller.error, isNotNull);
    },
    timeout: const Timeout(Duration(seconds: 2)),
  );
}
