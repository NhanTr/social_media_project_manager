import 'dart:async';
import 'dart:math';

import 'package:flutter/foundation.dart';

import '../../core/storage/post_local_store.dart';
import 'chat_repository.dart';
export 'chat_repository.dart';

/// Account-scoped state. All durable mutations share one serial lane; network
/// ACKs/events reconcile through that lane and never remove an unpersisted item.
class ChatController extends ChangeNotifier {
  final ChatRepository repository;
  final int accountId;
  final PostLocalStore store;
  final Future<void> Function(Duration)? _delayOverride;
  final Map<Timer, Completer<void>> _waits = {};
  ChatController({
    required this.repository,
    required this.accountId,
    required this.store,
    Future<void> Function(Duration)? delay,
  }) : _delayOverride = delay {
    if (accountId <= 0 ||
        repository.accountId != accountId ||
        store.accountId != accountId) {
      throw ArgumentError('Chat account scope mismatch');
    }
  }
  List<ChatConversation> _conversations = [];
  List<ChatConversation> get conversations => List.unmodifiable(_conversations);
  List<ChatMessage> _messages = [];
  ChatConnection connection = ChatConnection.disconnected;
  String? error;
  bool _disposed = false,
      _foreground = true,
      _storageReady = false,
      _demoOffline = false;
  int? _room;
  final Map<int, String?> _cursors = {};
  final Set<int> _loading = {};
  final Map<int, ChatMessage> _read = {}, _delivered = {};
  Future<void> _serial = Future<void>.value();
  Future<void>? _initializeFuture, _connectFuture, _flushFuture, _retryFuture;
  StreamSubscription<ChatEvent>? _subscription;
  final Random _random = Random.secure();
  List<ChatMessage> messagesFor(int id) =>
      List.unmodifiable(_messages.where((m) => m.conversationId == id));
  bool hasMore(int id) => _cursors[id] != null;
  bool isLoading(int id) => _loading.contains(id);
  bool get isDemo => repository.isDemo;
  Future<void> _wait(Duration duration) {
    if (_disposed) return Future<void>.value();
    if (_delayOverride != null) return _delayOverride(duration);
    final completer = Completer<void>();
    late Timer timer;
    timer = Timer(duration, () {
      _waits.remove(timer);
      completer.complete();
    });
    _waits[timer] = completer;
    return completer.future;
  }

  void _cancelWaits() {
    for (final entry in _waits.entries) {
      entry.key.cancel();
      if (!entry.value.isCompleted) entry.value.complete();
    }
    _waits.clear();
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  void _error(Object e) {
    if (!_disposed) {
      error = 'Không thể cập nhật trò chuyện. Hãy thử lại. ($e)';
      _notify();
    }
  }

  Future<void> _enqueue(Future<void> Function() action) {
    final next = _serial.then((_) async {
      if (!_disposed) await action();
    });
    _serial = next.catchError((Object e) {
      _error(e);
    });
    return _serial;
  }

  Map<String, dynamic> _snapshot(List<ChatMessage> messages) => {
    'messages': messages.map((m) => m.toJson()).toList(),
    'conversations': _conversations
        .map((c) => {'id': c.id, 'title': c.title})
        .toList(),
  };
  Future<void> _save(List<ChatMessage> next) async {
    if (_disposed || !_storageReady) return;
    await store.write('chat_client', _snapshot(next));
    if (_disposed) return;
    _messages = next..sort(compareMessages);
    _notify();
  }

  Future<void> initialize() =>
      _initializeFuture ??= _initialize().whenComplete(() {
        if (!_storageReady) _initializeFuture = null;
      });
  Future<void> _initialize() async {
    try {
      final snapshot = await store.read('chat_client');
      if (_disposed) return;
      if (snapshot != null) {
        _messages =
            (snapshot['messages'] as List)
                .map(
                  (v) =>
                      ChatMessage.fromJson(Map<String, dynamic>.from(v as Map)),
                )
                .toList()
              ..sort(compareMessages);
        _conversations = (snapshot['conversations'] as List)
            .map((v) => ChatConversation(v['id'] as int, v['title'] as String))
            .toList();
        if (_messages.any((m) => m.senderId != accountId && m.id == null)) {
          throw const FormatException('Invalid cached message');
        }
      }
      _storageReady = true;
      _subscription = repository.events.listen(
        _event,
        onError: (Object e) {
          _error(e);
          connection = ChatConnection.disconnected;
          _notify();
        },
      );
      _notify();
      await _connect();
    } catch (e) {
      _error(e);
    }
  }

  Future<void> reconnect() async {
    if (_disposed) return;
    await initialize();
    if (!_disposed) await _connect();
  }

  Future<void> _connect() => _connectFuture ??= _connectOnce().whenComplete(
    () => _connectFuture = null,
  );
  Future<void> _connectOnce() async {
    if (_disposed || !_foreground || !_storageReady || _demoOffline) return;
    connection = ChatConnection.connecting;
    _notify();
    try {
      // Conversation metadata is also locally available in demo/offline mode.
      final list = await repository.conversations();
      if (_disposed) return;
      await _enqueue(() async {
        _conversations = list;
        await _save(_messages.toList());
      });
      await repository.connect();
      if (_disposed || !_foreground) {
        await repository.disconnect();
        return;
      }
      connection = ChatConnection.connected;
      error = null;
      _notify();
      for (final c in _conversations) {
        if (_disposed || !_foreground) return;
        await _latest(c.id, recover: true);
      }
      await _flush();
    } catch (e) {
      if (!_disposed) {
        connection = ChatConnection.disconnected;
        _error(e);
      }
    }
  }

  Future<void> openConversation(int id) async {
    await initialize();
    if (!_conversations.any((c) => c.id == id)) {
      throw const ChatFailure(
        'Hội thoại không tồn tại hoặc không có quyền truy cập',
        terminal: true,
      );
    }
    if (connection == ChatConnection.connected) await _latest(id);
  }

  List<ChatMessage> _merged(List<ChatMessage> incoming) {
    final result = _messages.toList();
    for (final message in incoming) {
      final index = result.indexWhere(
        (m) =>
            m.conversationId == message.conversationId &&
            ((message.id != null && m.id == message.id) ||
                (m.senderId == message.senderId &&
                    m.clientMessageId == message.clientMessageId)),
      );
      if (index < 0) {
        result.add(message);
        continue;
      }
      final old = result[index];
      final status = old.status.index > message.status.index && old.id != null
          ? old.status
          : message.status;
      result[index] = message.copyWith(
        id: message.id ?? old.id,
        status: status,
      );
    }
    return result..sort(compareMessages);
  }

  Future<bool> _merge(List<ChatMessage> items) async {
    var persisted = false;
    await _enqueue(() async {
      await _save(_merged(items));
      persisted = !_disposed && _storageReady;
    });
    return persisted;
  }

  Future<void> _latest(int id, {bool recover = false}) async {
    final known = messagesFor(id)
        .where((m) => m.id != null)
        .map((m) => m.id)
        .toSet();
    String? cursor;
    final visited = <String>{};
    do {
      final page = await repository.history(id, cursor: cursor);
      if (_disposed) return;
      if (!await _merge(page.items)) {
        throw const ChatFailure('Không thể lưu lịch sử');
      }
      if (_disposed) return;
      _cursors[id] = page.nextCursor;
      await _ackDelivered(id, page.items);
      final overlap = page.items.any((m) => known.contains(m.id));
      cursor = page.nextCursor;
      if (!recover || known.isEmpty || overlap || cursor == null) break;
      if (!visited.add(cursor)) throw const ChatFailure('Cursor lịch sử lặp');
    } while (!_disposed && _foreground);
  }

  Future<void> loadOlder(int id) async {
    final cursor = _cursors[id];
    if (_disposed || _loading.contains(id) || cursor == null) return;
    _loading.add(id);
    _notify();
    try {
      final page = await repository.history(id, cursor: cursor);
      if (_disposed) return;
      if (!await _merge(page.items)) {
        throw const ChatFailure('Không thể lưu lịch sử');
      }
      if (_disposed) return;
      _cursors[id] = page.nextCursor;
      await _ackDelivered(id, page.items);
    } catch (e) {
      _error(e);
    } finally {
      _loading.remove(id);
      _notify();
    }
  }

  String _key() {
    final bytes = List<int>.generate(16, (_) => _random.nextInt(256));
    bytes[6] = (bytes[6] & 15) | 64;
    bytes[8] = (bytes[8] & 63) | 128;
    final hex = bytes.map((v) => v.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }

  Future<void> sendText(int id, String text) async {
    await initialize();
    if (_disposed || !_storageReady) return;
    final content = text.trim();
    if (content.isEmpty ||
        content.length > 5000 ||
        !_conversations.any((c) => c.id == id)) {
      _error(
        const ChatFailure(
          'Tin nhắn không hợp lệ hoặc hội thoại không khả dụng',
          terminal: true,
        ),
      );
      return;
    }
    final message = ChatMessage(
      conversationId: id,
      senderId: accountId,
      clientMessageId: _key(),
      content: content,
      sentAt: DateTime.now().toUtc(),
      status: MessageStatus.pending,
    );
    await _enqueue(() async {
      await _save([..._messages, message]);
    });
    await _flush();
  }

  Future<void> retry(String key) async {
    await _enqueue(() async {
      final next = _messages
          .map(
            (m) =>
                m.clientMessageId == key &&
                    m.senderId == accountId &&
                    m.status == MessageStatus.failed
                ? m.copyWith(status: MessageStatus.pending)
                : m,
          )
          .toList();
      await _save(next);
    });
    await _flush();
  }

  Future<void> _flush() =>
      _flushFuture ??= _flushOnce().whenComplete(() => _flushFuture = null);
  Future<void> _flushOnce() async {
    if (_disposed || !_foreground || connection != ChatConnection.connected) {
      return;
    }
    while (!_disposed &&
        _foreground &&
        connection == ChatConnection.connected) {
      final pending = _messages.where(
        (m) =>
            m.senderId == accountId &&
            m.status == MessageStatus.pending &&
            m.id == null,
      );
      if (pending.isEmpty) return;
      final message = pending.first;
      var acknowledged = false;
      for (
        var attempt = 0;
        attempt < 3 &&
            !_disposed &&
            _foreground &&
            connection == ChatConnection.connected;
        attempt++
      ) {
        try {
          final canonical = await repository.send(message);
          if (_disposed) return;
          if (canonical.id == null ||
              canonical.senderId != accountId ||
              canonical.conversationId != message.conversationId ||
              canonical.clientMessageId != message.clientMessageId) {
            throw const ChatFailure('ACK không khớp', terminal: true);
          }
          await _merge([canonical]);
          acknowledged = _messages.any(
            (m) => m.clientMessageId == message.clientMessageId && m.id != null,
          );
          break;
        } catch (e) {
          if (_disposed) return;
          _error(e);
          if (e is ChatFailure && e.terminal) {
            await _enqueue(() async {
              await _save(
                _messages
                    .map(
                      (m) =>
                          m.clientMessageId == message.clientMessageId &&
                              m.id == null
                          ? m.copyWith(status: MessageStatus.failed)
                          : m,
                    )
                    .toList(),
              );
            });
            acknowledged = _messages.any(
              (m) =>
                  m.clientMessageId == message.clientMessageId &&
                  (m.status == MessageStatus.failed || m.id != null),
            );
            break;
          }
          if (attempt < 2) {
            await _wait(Duration(milliseconds: 200 * (1 << attempt)));
          }
        }
      }
      if (!acknowledged) {
        if (connection != ChatConnection.offline) {
          connection = ChatConnection.disconnected;
        }
        _notify();
        return;
      }
    }
  }

  void _event(ChatEvent event) {
    if (_disposed) return;
    if (event is MessageEvent) {
      if (!_conversations.any((c) => c.id == event.message.conversationId)) {
        return;
      }
      unawaited(
        _merge([event.message])
            .then(
              (persisted) => persisted
                  ? _ackDelivered(event.message.conversationId, [event.message])
                  : Future<void>.value(),
            )
            .catchError((Object e) {
              _error(e);
            }),
      );
    } else if (event is ReceiptEvent) {
      if (event.participantId == accountId ||
          ![
            MessageStatus.delivered,
            MessageStatus.read,
          ].contains(event.status)) {
        return;
      }
      if (event.lastMessageId == null) {
        unawaited(
          _latest(event.conversationId).catchError((Object e) {
            _error(e);
          }),
        );
        return;
      }
      unawaited(
        _enqueue(() async {
          final matches = _messages.where(
            (m) =>
                m.conversationId == event.conversationId &&
                m.id == event.lastMessageId &&
                m.senderId == accountId,
          );
          if (matches.isEmpty) return;
          final watermark = matches.first;
          await _save(
            _messages
                .map(
                  (m) =>
                      m.conversationId == event.conversationId &&
                          m.senderId == accountId &&
                          m.id != null &&
                          compareMessages(m, watermark) <= 0 &&
                          m.status.index < event.status.index
                      ? m.copyWith(status: event.status)
                      : m,
                )
                .toList(),
          );
        }),
      );
    } else if (event is ConnectionLost) {
      if (_demoOffline) return;
      connection = ChatConnection.disconnected;
      _notify();
      if (!_demoOffline) {
        unawaited(
          _retryFuture ??= _reconnectBounded().whenComplete(
            () => _retryFuture = null,
          ),
        );
      }
    }
  }

  Future<void> _reconnectBounded() async {
    for (
      var attempt = 0;
      attempt < 3 && !_disposed && _foreground && !_demoOffline;
      attempt++
    ) {
      await _wait(Duration(milliseconds: 300 * (1 << attempt)));
      if (_disposed || !_foreground || _demoOffline) return;
      await _connect();
      if (connection == ChatConnection.connected) return;
    }
  }

  Future<void> _ackDelivered(int id, List<ChatMessage> messages) async {
    if (_disposed || connection != ChatConnection.connected) return;
    final incoming =
        messages
            .where(
              (m) =>
                  m.id != null &&
                  m.senderId != accountId &&
                  _messages.any(
                    (saved) => saved.id == m.id && saved.conversationId == id,
                  ),
            )
            .toList()
          ..sort(compareMessages);
    if (incoming.isEmpty) return;
    final newest = incoming.last;
    if (_delivered[id] != null &&
        compareMessages(newest, _delivered[id]!) <= 0) {
      return;
    }
    await repository.delivered(id, newest.id!);
    if (!_disposed) _delivered[id] = newest;
  }

  void setForeground(bool value) {
    if (_disposed || _foreground == value) return;
    _foreground = value;
    if (!value) {
      _cancelWaits();
      connection = ChatConnection.disconnected;
      unawaited(
        repository.disconnect().catchError((Object e) {
          _error(e);
        }),
      );
    } else {
      unawaited(reconnect());
    }
    _notify();
  }

  void setRoomVisible(int? id) {
    if (!_disposed) _room = id;
  }

  Future<void> reportVisible(int id, Set<int> ids) => _enqueue(() async {
    if (!_foreground || _room != id || connection != ChatConnection.connected) {
      return;
    }
    final visible =
        messagesFor(id)
            .where(
              (m) =>
                  m.senderId != accountId && m.id != null && ids.contains(m.id),
            )
            .toList()
          ..sort(compareMessages);
    if (visible.isEmpty) return;
    final watermark = visible.last;
    if (_read[id] != null && compareMessages(watermark, _read[id]!) <= 0) {
      return;
    }
    await repository.read(id, watermark.id!);
    if (!_disposed) _read[id] = watermark;
  });
  Future<void> setDemoOffline(bool offline) async {
    if (_disposed || !repository.isDemo) return;
    _demoOffline = offline;
    if (offline) _cancelWaits();
    await repository.setOffline(offline);
    if (_disposed) return;
    if (offline) {
      connection = ChatConnection.offline;
      await repository.disconnect();
      _notify();
    } else {
      await reconnect();
    }
  }

  Future<void> get idle async {
    await _serial;
    await _flushFuture;
    await _serial;
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _room = null;
    _cancelWaits();
    unawaited(_subscription?.cancel());
    unawaited(repository.dispose().catchError((Object _) {}));
    super.dispose();
  }
}
