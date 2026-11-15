import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/subscription/subscription_controller.dart';
import 'package:loccoc_application/features/subscription/subscription_screen.dart';
import 'package:loccoc_application/features/subscription/ai_generation_screen.dart';

import 'support/subscription_fixtures.dart';

void main() {
  final now = DateTime.utc(2030, 11, 12, 12);

  testWidgets(
    'Free comparison labels sandbox and cannot unlock AI from checkout return',
    (t) async {
      var paid = false;
      Uri? launched;
      final c = SubscriptionController(
        api: subscriptionApi(
          (r) async => jsonResponse(switch (r.url.path) {
            '/api/v1/subscriptions/plans' => planFixture,
            '/api/v1/subscriptions/checkout' => checkoutFixture(),
            _ => subscriptionFixture(
              tier: paid ? 'PLUS' : 'FREE',
              ai: paid,
              payment: paid ? 'SUCCEEDED' : 'PENDING',
            ),
          }),
        ),
        launchCheckout: (url) async {
          launched = url;
          return true;
        },
        now: () => now,
      );
      await t.pumpWidget(MaterialApp(home: SubscriptionScreen(controller: c)));
      await t.pumpAndSettle();
      expect(find.text('Gói hiện tại: Free'), findsOneWidget);
      expect(find.textContaining('49.000 ₫ / tháng'), findsOneWidget);
      expect(find.textContaining('Sandbox'), findsWidgets);
      expect(c.canUseAi, isFalse);
      await t.scrollUntilVisible(find.byKey(const Key('checkout')), 250);
      await t.tap(find.byKey(const Key('checkout')));
      await t.pumpAndSettle();
      expect(launched?.scheme, 'https');
      expect(c.snapshot?.tier, 'FREE');
      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]) {
        t.binding.handleAppLifecycleStateChanged(state);
      }
      await t.pumpAndSettle();
      expect(c.canUseAi, isFalse);
      await t.drag(find.byType(ListView), const Offset(0, 2000));
      await t.pumpAndSettle();
      expect(
        find.textContaining('Đang chờ xác nhận thanh toán'),
        findsOneWidget,
      );
      paid = true;
      await t.pump(const Duration(seconds: 3));
      await t.pumpAndSettle();
      await t.drag(find.byType(ListView), const Offset(0, 2000));
      await t.pumpAndSettle();
      expect(find.text('Gói hiện tại: Plus'), findsOneWidget);
      expect(c.canUseAi, isTrue);
      await t.scrollUntilVisible(find.byKey(const Key('open-ai')), 250);
      expect(
        t.widget<FilledButton>(find.byKey(const Key('open-ai'))).onPressed,
        isNotNull,
      );
      await t.pumpWidget(const SizedBox());
      c.dispose();
    },
  );

  testWidgets(
    'Plus cancellation asks first and retains granted AI until server expiry',
    (t) async {
      var cancellations = 0;
      final c = SubscriptionController(
        api: subscriptionApi((r) async {
          if (r.url.path.endsWith('cancel')) {
            cancellations++;
            return jsonResponse({}, 202);
          }
          return jsonResponse(
            r.url.path.endsWith('plans')
                ? planFixture
                : subscriptionFixture(
                    tier: 'PLUS',
                    ai: true,
                    cancel: cancellations > 0,
                  ),
          );
        }),
        now: () => now,
      );
      await t.pumpWidget(MaterialApp(home: SubscriptionScreen(controller: c)));
      await t.pumpAndSettle();
      await t.scrollUntilVisible(find.byKey(const Key('cancel-renewal')), 250);
      await t.pumpAndSettle();
      await t.ensureVisible(find.byKey(const Key('cancel-renewal')));
      await t.pumpAndSettle();
      await t.tap(find.byKey(const Key('cancel-renewal')));
      await t.pumpAndSettle();
      expect(cancellations, 0);
      expect(find.byType(AlertDialog), findsOneWidget);
      await t.tap(find.text('Xác nhận hủy'));
      await t.pumpAndSettle();
      expect(cancellations, 1);
      await t.drag(find.byType(ListView), const Offset(0, 2000));
      await t.pumpAndSettle();
      expect(find.textContaining('Đã hủy gia hạn'), findsOneWidget);
      expect(c.canUseAi, isTrue);
      await t.pumpWidget(const SizedBox());
      c.dispose();
    },
  );

  testWidgets('missing backend stays unavailable and retry can recover', (
    t,
  ) async {
    var available = false;
    final c = SubscriptionController(
      api: subscriptionApi(
        (r) async => available
            ? jsonResponse(
                r.url.path.endsWith('plans')
                    ? planFixture
                    : subscriptionFixture(),
              )
            : jsonResponse({}, 404),
      ),
      now: () => now,
    );
    await t.pumpWidget(MaterialApp(home: SubscriptionScreen(controller: c)));
    await t.pumpAndSettle();
    expect(find.text('Gói hiện tại: Free'), findsNothing);
    expect(find.textContaining('Không thể tải quyền lợi'), findsOneWidget);
    expect(c.canUseAi, isFalse);
    available = true;
    await t.tap(find.byTooltip('Làm mới quyền lợi'));
    await t.pumpAndSettle();
    expect(find.text('Gói hiện tại: Free'), findsOneWidget);
    await t.pumpWidget(const SizedBox());
    c.dispose();
  });

  testWidgets('failed payment stays visible and offers sandbox retry', (
    t,
  ) async {
    final c = SubscriptionController(
      api: subscriptionApi(
        (r) async => jsonResponse(
          r.url.path.endsWith('plans')
              ? planFixture
              : subscriptionFixture(payment: 'FAILED'),
        ),
      ),
      now: () => now,
    );
    await t.pumpWidget(MaterialApp(home: SubscriptionScreen(controller: c)));
    await t.pumpAndSettle();
    expect(find.textContaining('Thanh toán chưa thành công'), findsOneWidget);
    expect(c.canCheckout, isTrue);
    expect(c.canUseAi, isFalse);
    await t.pumpWidget(const SizedBox());
    c.dispose();
  });

  testWidgets(
    'AI entry locks without grant and exposes real job after authorized submit',
    (t) async {
      var allowed = false;
      var creations = 0;
      final c = SubscriptionController(
        api: subscriptionApi((r) async {
          if (r.url.path == '/api/v1/ai/generations') {
            creations++;
            return jsonResponse({
              'id': 7,
              'status': 'PENDING',
              'generatedMediaUrl': null,
            }, 202);
          }
          return jsonResponse(
            r.url.path.endsWith('plans')
                ? planFixture
                : subscriptionFixture(tier: 'PLUS', ai: allowed),
          );
        }),
        now: () => now,
      );
      await t.pumpWidget(MaterialApp(home: AiGenerationScreen(controller: c)));
      await t.pumpAndSettle();
      await t.enterText(find.byType(TextField), 'Một khu vườn');
      expect(
        t.widget<FilledButton>(find.byKey(const Key('generate-ai'))).onPressed,
        isNull,
      );
      expect(creations, 0);
      allowed = true;
      await t.tap(find.byTooltip('Làm mới quyền lợi'));
      await t.pumpAndSettle();
      await t.tap(find.byKey(const Key('generate-ai')));
      await t.pumpAndSettle();
      expect(creations, 1);
      expect(find.textContaining('#7'), findsOneWidget);
      expect(find.textContaining('Đang chờ xử lý'), findsOneWidget);
      allowed = false;
      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]) {
        t.binding.handleAppLifecycleStateChanged(state);
      }
      await t.pumpAndSettle();
      expect(
        t.widget<FilledButton>(find.byKey(const Key('generate-ai'))).onPressed,
        isNull,
      );
      await t.pumpWidget(const SizedBox());
      c.dispose();
    },
  );

  testWidgets('paywall remains usable on a narrow screen with large text', (
    t,
  ) async {
    t.view.physicalSize = const Size(360, 740);
    t.view.devicePixelRatio = 1;
    addTearDown(t.view.resetPhysicalSize);
    addTearDown(t.view.resetDevicePixelRatio);
    final c = SubscriptionController(
      api: subscriptionApi(
        (r) async => jsonResponse(
          r.url.path.endsWith('plans') ? planFixture : subscriptionFixture(),
        ),
      ),
      now: () => now,
    );
    await t.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: TextScaler.linear(1.8)),
          child: child!,
        ),
        home: SubscriptionScreen(controller: c),
      ),
    );
    await t.pumpAndSettle();
    await t.scrollUntilVisible(find.byKey(const Key('checkout')), 250);
    expect(t.takeException(), isNull);
    await t.pumpWidget(const SizedBox());
    c.dispose();
  });
}
