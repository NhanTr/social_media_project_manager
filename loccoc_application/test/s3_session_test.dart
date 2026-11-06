import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/s3/s3_session.dart';

void main() {
  test(
    'staging and production never silently create simulated conversations',
    () {
      expect(
        S3Session.forEnvironment(accountId: 1, environment: 'staging'),
        isNull,
      );
      expect(
        S3Session.forEnvironment(accountId: 1, environment: 'prod'),
        isNull,
      );
      expect(
        S3Session.forEnvironment(
          accountId: 1,
          environment: 'dev',
          demoEnabled: false,
        ),
        isNull,
      );
    },
  );
  test(
    'demo composition keeps a recreated account and its durable state separate',
    () async {
      final dir = await Directory.systemTemp.createTemp('loccoc-s3-session');
      final one = S3Session.forEnvironment(
        accountId: 1,
        environment: 'dev',
        directory: dir.path,
      )!;
      final two = S3Session.forEnvironment(
        accountId: 2,
        environment: 'dev',
        directory: dir.path,
      )!;
      try {
        await one.initialize();
        await two.initialize();
        expect(one.chat.conversations, isNotEmpty);
        expect(one.notifications.items, isNotEmpty);
        expect(one.accountId, 1);
        expect(two.accountId, 2);
      } finally {
        one.dispose();
        two.dispose();
        await dir.delete(recursive: true);
      }
    },
  );
}
