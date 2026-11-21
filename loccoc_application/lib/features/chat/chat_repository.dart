import 'chat_models.dart';
export 'chat_models.dart';

/// Connect subscribes before completing. Replay returns the canonical message
/// for the original key; a transport receipt is never a business ACK.
abstract class ChatRepository {
  int get accountId;
  Stream<ChatEvent> get events;
  bool get isDemo => false;
  Future<void> connect();
  Future<void> disconnect();
  Future<void> dispose() => disconnect();
  Future<List<ChatConversation>> conversations();
  Future<MessagePage> history(int conversationId, {String? cursor});
  Future<ChatMessage> send(ChatMessage message);
  Future<void> delivered(int conversationId, int lastMessageId);
  Future<void> read(int conversationId, int lastMessageId);
  Future<void> setOffline(bool offline) async {}
}
