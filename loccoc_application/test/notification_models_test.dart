import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/notifications/notification_models.dart';

void main() {
  final types = {
    'FRIEND_REQUEST': ('FRIEND_REQUEST', NotificationTargetKind.friendRequest),
    'FRIEND_ACCEPT': ('PROFILE', NotificationTargetKind.profile),
    'NEW_POST': ('POST', NotificationTargetKind.post),
    'CHAT_MESSAGE': ('CONVERSATION', NotificationTargetKind.conversation),
    'SUBSCRIPTION_UPDATED': (
      'SUBSCRIPTION',
      NotificationTargetKind.subscription,
    ),
    'REPORT_RESULT': ('REPORT', NotificationTargetKind.report),
  };
  for (final entry in types.entries) {
    test('parses ${entry.key} only with its typed target', () {
      final result = NotificationTarget.parse(
        type: entry.key,
        target: {
          'kind': entry.value.$1,
          'id': 10,
          if (entry.key == 'CHAT_MESSAGE') 'messageId': 99,
        },
      );
      expect(result.kind, entry.value.$2);
      expect(result.id, 10);
      expect(result.messageId, entry.key == 'CHAT_MESSAGE' ? 99 : null);
    });
  }
  test('null title and body display safe fallback without dropping inbox', () {
    final item = NotificationItem.fromJson({
      'id': 501,
      'userId': 2,
      'type': 'NEW_POST',
      'title': null,
      'body': null,
      'read': false,
      'createdAt': '2026-11-06T07:40:00Z',
      'target': {'kind': 'POST', 'id': 301},
    });
    expect(item.title, 'Thông báo');
    expect(item.body, '');
    expect(item.target.id, 301);
  });
  test('never treats legacy chat referenceId as a conversation', () {
    final item = NotificationItem.fromJson({
      'id': 501,
      'userId': 2,
      'type': 'CHAT_MESSAGE',
      'referenceId': 99,
      'title': 'Message',
      'body': 'Hello',
      'read': false,
      'createdAt': '2026-11-06T07:40:00Z',
    });
    expect(item.target.kind, NotificationTargetKind.unavailable);
  });
  test(
    'unknown, mismatched, invalid and deleted targets are safe fallbacks',
    () {
      for (final target in [
        null,
        'url',
        {'kind': 'POST', 'id': 0},
        {'kind': 'POST', 'id': '10'},
        {'kind': 'PROFILE', 'id': 10},
        {'kind': 'POST', 'id': 10, 'available': false},
        {'kind': 'POST', 'id': 10, 'status': 'DELETED'},
        {'kind': 'POST', 'id': 10, 'status': 'FORBIDDEN'},
      ]) {
        expect(
          NotificationTarget.parse(
            type: 'NEW_POST',
            target: target,
          ).isAvailable,
          false,
        );
      }
      expect(
        NotificationTarget.parse(
          type: 'UNKNOWN',
          target: {'kind': 'POST', 'id': 10},
        ).isAvailable,
        false,
      );
    },
  );
  test('FCM strings and REST target use identical chat routing semantics', () {
    final payload = NotificationPushPayload.parse({
      'notificationId': '501',
      'type': 'CHAT_MESSAGE',
      'targetKind': 'CONVERSATION',
      'targetId': '10',
      'messageId': '99',
    });
    expect(payload.notificationId, 501);
    expect(payload.target.kind, NotificationTargetKind.conversation);
    expect(payload.target.id, 10);
    expect(payload.target.messageId, 99);
  });
  test('malformed push never throws or accepts arbitrary navigation', () {
    for (final data in <Map<String, dynamic>>[
      {},
      {
        'notificationId': 501,
        'type': 'NEW_POST',
        'targetKind': 'POST',
        'targetId': '10',
      },
      {'notificationId': '501', 'type': 'CHAT_MESSAGE', 'targetId': '99'},
      {
        'notificationId': 'bad',
        'type': 'NEW_POST',
        'targetKind': 'POST',
        'targetId': '10',
      },
      {
        'notificationId': '501',
        'type': 'NEW_POST',
        'targetKind': 'POST',
        'targetId': '-1',
      },
      {
        'notificationId': '501',
        'type': 'NEW_POST',
        'targetKind': 'POST',
        'targetId': '10',
        'messageId': 'oops',
      },
      {'url': 'https://evil.test', 'type': 'NEW_POST'},
    ]) {
      expect(NotificationPushPayload.parse(data).target.isAvailable, false);
    }
  });
}
