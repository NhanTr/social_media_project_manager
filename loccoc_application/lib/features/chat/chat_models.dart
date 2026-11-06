enum MessageStatus { pending, failed, sent, delivered, read }

enum ChatConnection { disconnected, connecting, connected, offline }

class ChatMessage {
  final int? id;
  final int conversationId, senderId;
  final String clientMessageId, content;
  final DateTime sentAt;
  final MessageStatus status;
  const ChatMessage({
    this.id,
    required this.conversationId,
    required this.senderId,
    required this.clientMessageId,
    required this.content,
    required this.sentAt,
    this.status = MessageStatus.sent,
  });
  ChatMessage copyWith({int? id, MessageStatus? status}) => ChatMessage(
    id: id ?? this.id,
    conversationId: conversationId,
    senderId: senderId,
    clientMessageId: clientMessageId,
    content: content,
    sentAt: sentAt,
    status: status ?? this.status,
  );
  Map<String, dynamic> toJson() => {
    'id': id,
    'conversationId': conversationId,
    'senderId': senderId,
    'clientMessageId': clientMessageId,
    'content': content,
    'sentAt': sentAt.toIso8601String(),
    'status': status.name,
  };
  factory ChatMessage.fromJson(Map<String, dynamic> j) => ChatMessage(
    id: j['id'] as int?,
    conversationId: j['conversationId'] as int,
    senderId: j['senderId'] as int,
    clientMessageId: j['clientMessageId'] as String,
    content: j['content'] as String,
    sentAt: DateTime.parse(j['sentAt'] as String),
    status: MessageStatus.values.byName(j['status'] as String),
  );
}

class ChatConversation {
  final int id;
  final String title;
  const ChatConversation(this.id, this.title);
}

class MessagePage {
  final List<ChatMessage> items;
  final String? nextCursor;
  const MessagePage(this.items, {this.nextCursor});
}

sealed class ChatEvent {
  const ChatEvent();
}

class MessageEvent extends ChatEvent {
  final ChatMessage message;
  const MessageEvent(this.message);
}

class ReceiptEvent extends ChatEvent {
  final int conversationId, participantId;
  final int? lastMessageId;
  final MessageStatus status;
  const ReceiptEvent({
    required this.conversationId,
    required this.participantId,
    required this.lastMessageId,
    required this.status,
  });
}

class ConnectionLost extends ChatEvent {
  const ConnectionLost();
}

class ChatFailure implements Exception {
  final String message;
  final bool terminal;
  const ChatFailure(this.message, {this.terminal = false});
  @override
  String toString() => message;
}

int compareMessages(ChatMessage a, ChatMessage b) {
  final time = a.sentAt.compareTo(b.sentAt);
  return time != 0 ? time : (a.id ?? 0).compareTo(b.id ?? 0);
}
