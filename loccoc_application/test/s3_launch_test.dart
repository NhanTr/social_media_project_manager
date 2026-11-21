import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/s3/notification_launch_controller.dart';

void main() {
  const payload = {
    'notificationId': '501',
    'type': 'CHAT_MESSAGE',
    'targetKind': 'CONVERSATION',
    'targetId': '10',
    'messageId': '99',
  };
  test('cold-start target stays pending until authenticated consumer takes it once', () {
    final launch = NotificationLaunchController(initialPayload: payload);
    expect(launch.hasPending, isTrue);
    final value = launch.takeForAccount(1);
    expect(value?.target.id, 10);
    expect(value?.target.messageId, 99);
    expect(launch.takeForAccount(1), isNull);
    launch.offer(payload);
    expect(launch.takeForAccount(1), isNull);
    launch.dispose();
  });
  test('account-bound launch cannot navigate a different account', () {
    final launch = NotificationLaunchController();
    launch.offer(payload, recipientAccountId: 1);
    expect(launch.takeForAccount(2), isNull);
    expect(launch.hasPending, isFalse);
    launch.dispose();
  });
  test('invalid initial payload becomes safe fallback, never a guessed conversation', () {
    final launch = NotificationLaunchController(
      initialPayload: {
        'notificationId': '501',
        'type': 'CHAT_MESSAGE',
        'referenceId': '99',
      },
    );
    expect(launch.takeForAccount(1)?.target.isAvailable, isFalse);
    launch.dispose();
  });
}
