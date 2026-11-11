import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/testing.dart';
import 'package:loccoc_application/core/models/user_models.dart';
import 'package:loccoc_application/core/network/api_client.dart';
import 'package:loccoc_application/features/auth/auth_controller.dart';
import 'package:loccoc_application/features/shell/app_shell.dart';

import 'support/subscription_fixtures.dart';

class SubscriptionAuth extends AuthController {
  SubscriptionAuth(ApiClient api) : super(api: api);
  int userId = 1;
  @override
  AuthStatus get status => AuthStatus.authenticated;
  @override
  UserModel get currentUser => UserModel(
    id: userId,
    username: 'tester$userId',
    displayName: 'Tester $userId',
  );
  void switchAccount() {
    userId = 2;
    notifyListeners();
  }
}

void main() {
  testWidgets(
    'profile reaches paywall and account replacement discards old Plus state',
    (t) async {
      var plus = true;
      final auth = SubscriptionAuth(
        ApiClient(
          storage: SubscriptionSession(),
          client: MockClient(
            (r) async => switch (r.url.path) {
              '/api/v1/subscriptions/plans' => jsonResponse(planFixture),
              '/api/v1/subscriptions/me' => jsonResponse(
                subscriptionFixture(tier: plus ? 'PLUS' : 'FREE', ai: plus),
              ),
              '/api/v1/feed' => jsonResponse({
                'items': [],
                'hasNext': false,
                'nextCursor': null,
              }),
              '/api/v1/friends' => jsonResponse([]),
              _ => jsonResponse([]),
            },
          ),
        ),
      );
      await t.pumpWidget(
        MaterialApp(
          home: AppShell(authController: auth, s3SessionFactory: (_) => null),
        ),
      );
      await t.pumpAndSettle();
      await t.tap(find.text('Cá nhân'));
      await t.pumpAndSettle();
      await t.scrollUntilVisible(
        find.byKey(const Key('profile-subscription')),
        200,
      );
      await t.tap(find.byKey(const Key('profile-subscription')));
      await t.pumpAndSettle();
      expect(find.text('Gói hiện tại: Plus'), findsOneWidget);
      plus = false;
      auth.switchAccount();
      await t.pumpAndSettle();
      expect(find.text('Gói hiện tại: Plus'), findsNothing);
      await t.tap(find.text('Cá nhân'));
      await t.pumpAndSettle();
      await t.scrollUntilVisible(
        find.byKey(const Key('profile-subscription')),
        -200,
      );
      await t.tap(find.byKey(const Key('profile-subscription')));
      await t.pumpAndSettle();
      expect(find.text('Gói hiện tại: Free'), findsOneWidget);
      expect(find.text('AI đã được mở cho tài khoản'), findsNothing);
      await t.pumpWidget(const SizedBox());
      auth.dispose();
    },
  );
}
