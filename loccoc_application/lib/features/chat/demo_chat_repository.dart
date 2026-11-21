import 'dart:async';
import 'dart:convert';

import '../../core/storage/post_local_store.dart';
import 'chat_repository.dart';

/// A local simulation, deliberately separate from the production adapter.
/// Canonical server state is durable too: a lost ACK replay after restart must
/// not insert a second message.
class DemoChatRepository extends ChatRepository {
  @override
  final int accountId;
  final PostLocalStore store;
  DemoChatRepository({required this.accountId, required this.store}) {
    if (accountId <= 0 || store.accountId != accountId) {
      throw ArgumentError('Chat account scope mismatch');
    }
  }
  @override
  bool get isDemo => true;
  final _events = StreamController<ChatEvent>.broadcast(sync: true);
  @override
  Stream<ChatEvent> get events => _events.stream;
  List<ChatMessage> _messages = [];
  Future<void> _serial = Future<void>.value();
  bool _loaded = false, _offline = false, _connected = false, _disposed = false;
  Future<T> _run<T>(Future<T> Function() action) {
    final next = _serial.then((_) async {
      if (_disposed) throw const ChatFailure('Phiên đã đóng', terminal: true);
      await _load();
      return action();
    });
    _serial = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return next;
  }

  Future<void> _load() async {
    if (_loaded) return;
    final snapshot = await store.read('chat_demo_server');
    if (_disposed) return;
    if (snapshot == null) {
      final peer = accountId == 2 ? 1 : 2;
      final start = DateTime.now().toUtc().subtract(
        const Duration(minutes: 12),
      );
      _messages = List.generate(
        12,
        (i) => ChatMessage(
          id: i + 88,
          conversationId: 10,
          senderId: i.isEven ? peer : accountId,
          clientMessageId: 'demo-$accountId-${i + 1}',
          content: i == 11
              ? 'Hẹn bạn ở thư viện nhé!'
              : 'Tin nhắn mô phỏng ${i + 1}',
          sentAt: start.add(Duration(minutes: i)),
        ),
      );
      await _persist(_messages);
    } else {
      _messages = (snapshot['messages'] as List)
          .map((v) => ChatMessage.fromJson(Map<String, dynamic>.from(v as Map)))
          .toList();
    }
    _loaded = true;
  }

  Future<void> _persist(List<ChatMessage> messages) => store.write(
    'chat_demo_server',
    {'messages': messages.map((m) => m.toJson()).toList()},
  );
  void _network() {
    if (_offline || !_connected) {
      throw const ChatFailure('Đang mất kết nối mô phỏng');
    }
  }

  void _conversation(int id) {
    if (id != 10) {
      throw const ChatFailure('Hội thoại không khả dụng', terminal: true);
    }
  }

  @override
  Future<void> connect() => _run(() async {
    if (_offline) throw const ChatFailure('Đang ngoại tuyến mô phỏng');
    _connected = true;
  });
  @override
  Future<void> disconnect() async {
    _connected = false;
  }

  @override
  Future<List<ChatConversation>> conversations() =>
      _run(() async => [const ChatConversation(10, 'Minh · mô phỏng')]);
  @override
  Future<MessagePage> history(int id, {String? cursor}) => _run(() async {
    _network();
    _conversation(id);
    var sorted = _messages.where((m) => m.conversationId == id).toList()
      ..sort((a, b) => compareMessages(b, a));
    if (cursor != null) {
      try {
        final boundary =
            jsonDecode(utf8.decode(base64Url.decode(cursor))) as List;
        final time = DateTime.parse(boundary[0] as String);
        final boundaryId = boundary[1] as int;
        sorted = sorted
            .where(
              (m) =>
                  m.sentAt.isBefore(time) ||
                  (m.sentAt == time && m.id! < boundaryId),
            )
            .toList();
      } catch (_) {
        throw const ChatFailure('Cursor không hợp lệ', terminal: true);
      }
    }
    final page = sorted.take(30).toList();
    return MessagePage(
      page,
      nextCursor: sorted.length > page.length
          ? base64Url.encode(
              utf8.encode(
                jsonEncode([page.last.sentAt.toIso8601String(), page.last.id]),
              ),
            )
          : null,
    );
  });
  @override
  Future<ChatMessage> send(ChatMessage message) => _run(() async {
    _network();
    _conversation(message.conversationId);
    if (message.senderId != accountId ||
        message.clientMessageId.isEmpty ||
        message.clientMessageId.length > 100 ||
        message.content.trim().isEmpty ||
        message.content.length > 5000) {
      throw const ChatFailure('Tin nhắn không hợp lệ', terminal: true);
    }
    final existing = _messages.where(
      (m) =>
          m.conversationId == message.conversationId &&
          m.senderId == accountId &&
          m.clientMessageId == message.clientMessageId,
    );
    if (existing.isNotEmpty) {
      if (existing.first.content != message.content) {
        throw const ChatFailure('MESSAGE_REQUEST_CONFLICT', terminal: true);
      }
      return existing.first;
    }
    final id =
        _messages.fold<int>(0, (max, m) => m.id! > max ? m.id! : max) + 1;
    final canonical = message.copyWith(id: id, status: MessageStatus.sent);
    final next = [..._messages, canonical];
    await _persist(next);
    if (_disposed) throw const ChatFailure('Phiên đã đóng', terminal: true);
    _messages = next;
    _events.add(MessageEvent(canonical));
    return canonical;
  });
  Future<void> _receipt(int id, int lastMessageId, MessageStatus status) =>
      _run(() async {
        _network();
        _conversation(id);
        final target = _messages.where(
          (m) =>
              m.conversationId == id &&
              m.id == lastMessageId &&
              m.senderId != accountId,
        );
        if (target.isEmpty) {
          throw const ChatFailure('RECEIPT_TARGET_INVALID', terminal: true);
        }
        final watermark = target.first;
        final next = _messages
            .map(
              (m) =>
                  m.conversationId == id &&
                      m.senderId != accountId &&
                      compareMessages(m, watermark) <= 0 &&
                      m.status.index < status.index
                  ? m.copyWith(status: status)
                  : m,
            )
            .toList();
        await _persist(next);
        if (!_disposed) _messages = next;
      });
  @override
  Future<void> delivered(int id, int lastMessageId) =>
      _receipt(id, lastMessageId, MessageStatus.delivered);
  @override
  Future<void> read(int id, int lastMessageId) =>
      _receipt(id, lastMessageId, MessageStatus.read);
  @override
  Future<void> setOffline(bool offline) async {
    _offline = offline;
    if (offline) _connected = false;
  }

  @override
  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    _connected = false;
    await _serial;
    await _events.close();
  }
}
