import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/core/storage/post_local_store.dart';
import 'package:loccoc_application/features/chat/demo_chat_repository.dart';
import 'package:loccoc_application/features/chat/chat_repository.dart';

void main() {
  test(
    'demo canonical survives repository restart and replay cannot duplicate',
    () async {
      final dir = await Directory.systemTemp.createTemp('chat-demo');
      final store = PostLocalStore(accountId: 1, directory: dir.path);
      var repo = DemoChatRepository(accountId: 1, store: store);
      await repo.connect();
      final original = (await repo.history(10)).items.length;
      final message = ChatMessage(
        conversationId: 10,
        senderId: 1,
        clientMessageId: 'stable-client-key',
        content: 'Đã lưu',
        sentAt: DateTime.utc(2026),
        status: MessageStatus.pending,
      );
      final ack = await repo.send(message);
      expect(ack.id, isNotNull);
      await repo.dispose();
      repo = DemoChatRepository(accountId: 1, store: store);
      await repo.connect();
      final replay = await repo.send(message);
      expect(replay.id, ack.id);
      expect((await repo.history(10)).items.length, original + 1);
      await expectLater(
        repo.send(
          ChatMessage(
            conversationId: 10,
            senderId: 1,
            clientMessageId: 'stable-client-key',
            content: 'Nội dung khác',
            sentAt: DateTime.utc(2026),
          ),
        ),
        throwsA(isA<ChatFailure>()),
      );
      await expectLater(repo.history(999), throwsA(isA<ChatFailure>()));
      await repo.dispose();
      await dir.delete(recursive: true);
    },
  );
}
