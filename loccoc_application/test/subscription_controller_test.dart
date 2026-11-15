import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/subscription/subscription_controller.dart';

import 'support/subscription_fixtures.dart';

void main() {
  final now = DateTime.utc(2030, 11, 12, 12);

  test(
    'Plus tier alone never grants AI and checkout callback cannot grant it',
    () async {
      final c = SubscriptionController(
        api: subscriptionApi(
          (r) async => jsonResponse(switch (r.url.path) {
            '/api/v1/subscriptions/plans' => planFixture,
            '/api/v1/subscriptions/checkout' => checkoutFixture(),
            _ => subscriptionFixture(),
          }),
        ),
        launchCheckout: (_) async => true,
        now: () => now,
      );
      addTearDown(c.dispose);
      await c.refresh();
      await c.startCheckout();
      expect(c.paymentPending, isTrue);
      expect(c.snapshot?.tier, 'FREE');
      expect(c.canUseAi, isFalse);
      await c.reconcileCheckout();
      expect(c.snapshot?.tier, 'FREE');
      expect(c.canUseAi, isFalse);
    },
  );

  test(
    'delayed webhook grants access only after server snapshot refresh',
    () async {
      var paid = false;
      final c = SubscriptionController(
        api: subscriptionApi(
          (r) async => jsonResponse(
            r.url.path.endsWith('plans')
                ? planFixture
                : subscriptionFixture(
                    tier: paid ? 'PLUS' : 'FREE',
                    ai: paid,
                    payment: paid ? 'SUCCEEDED' : 'PENDING',
                  ),
          ),
        ),
        launchCheckout: (_) async => true,
        now: () => now,
      );
      addTearDown(c.dispose);
      await c.refresh();
      await c.reconcileCheckout();
      expect(c.paymentPending, isTrue);
      expect(c.canUseAi, isFalse);
      paid = true;
      await c.refresh();
      expect(c.paymentPending, isFalse);
      expect(c.canUseAi, isTrue);
    },
  );

  test('Plus without explicit grant remains locked, refresh failure revokes old grant', () async {
    var grant = false;
    var fail = false;
    final c = SubscriptionController(
      api: subscriptionApi(
        (r) async => fail
            ? jsonResponse({}, 503)
            : jsonResponse(
                r.url.path.endsWith('plans')
                    ? planFixture
                    : subscriptionFixture(tier: 'PLUS', ai: grant),
              ),
      ),
      now: () => now,
    );
    addTearDown(c.dispose);
    await c.refresh();
    expect(c.canUseAi, isFalse);
    grant = true;
    await c.refresh();
    expect(c.canUseAi, isTrue);
    fail = true;
    await c.refresh();
    expect(c.canUseAi, isFalse);
    expect(c.error, isNotNull);
  });

  test('stale or expired entitlement cannot authorize AI', () async {
    var clock = now;
    final c = SubscriptionController(
      api: subscriptionApi(
        (r) async => jsonResponse(
          r.url.path.endsWith('plans')
              ? planFixture
              : subscriptionFixture(tier: 'PLUS', ai: true),
        ),
      ),
      now: () => clock,
    );
    addTearDown(c.dispose);
    await c.refresh();
    expect(c.canUseAi, isTrue);
    clock = now.add(const Duration(minutes: 10));
    expect(c.canUseAi, isFalse);
  });

  test(
    'concurrent checkout taps are serialized and session is reused',
    () async {
      var creates = 0;
      var launches = 0;
      final barrier = Completer<void>();
      final c = SubscriptionController(
        api: subscriptionApi((r) async {
          if (r.url.path.endsWith('checkout')) {
            creates++;
            await barrier.future;
            return jsonResponse(checkoutFixture());
          }
          return jsonResponse(
            r.url.path.endsWith('plans') ? planFixture : subscriptionFixture(),
          );
        }),
        launchCheckout: (_) async {
          launches++;
          return true;
        },
        now: () => now,
      );
      addTearDown(c.dispose);
      await c.refresh();
      final first = c.startCheckout();
      await c.startCheckout();
      barrier.complete();
      await first;
      await c.startCheckout();
      expect(creates, 1);
      expect(launches, 2);
      expect(c.canUseAi, isFalse);
    },
  );

  test(
    'uncertain checkout retry preserves UUID and launch failure is recoverable',
    () async {
      final ids = <String>[];
      var launches = 0;
      final c = SubscriptionController(
        api: subscriptionApi((r) async {
          if (r.url.path.endsWith('checkout')) {
            ids.add(jsonDecode(r.body)['clientRequestId'] as String);
            return ids.length == 1
                ? jsonResponse({}, 503)
                : jsonResponse(checkoutFixture());
          }
          return jsonResponse(
            r.url.path.endsWith('plans') ? planFixture : subscriptionFixture(),
          );
        }),
        launchCheckout: (_) async => ++launches > 1,
        now: () => now,
      );
      addTearDown(c.dispose);
      await c.refresh();
      await c.startCheckout();
      expect(c.error, isNotNull);
      await c.startCheckout();
      expect(c.error, isNotNull);
      await c.startCheckout();
      expect(c.error, isNull);
      expect(ids.length, 2);
      expect(ids[0], ids[1]);
      expect(
        ids[0],
        matches(
          RegExp(
            r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
          ),
        ),
      );
    },
  );

  test('expired checkout URL is never launched', () async {
    var launched = false;
    final c = SubscriptionController(
      api: subscriptionApi(
        (r) async => jsonResponse(switch (r.url.path) {
          '/api/v1/subscriptions/plans' => planFixture,
          '/api/v1/subscriptions/checkout' => checkoutFixture(
            expiresAt: '2030-11-12T11:00:00Z',
          ),
          _ => subscriptionFixture(),
        }),
      ),
      launchCheckout: (_) async {
        launched = true;
        return true;
      },
      now: () => now,
    );
    addTearDown(c.dispose);
    await c.refresh();
    await c.startCheckout();
    expect(launched, isFalse);
    expect(c.error, isNotNull);
  });

  test(
    'disposed account drops late responses and cannot launch checkout',
    () async {
      final barrier = Completer<void>();
      final c = SubscriptionController(
        api: subscriptionApi((r) async {
          await barrier.future;
          return jsonResponse(
            r.url.path.endsWith('plans')
                ? planFixture
                : subscriptionFixture(tier: 'PLUS', ai: true),
          );
        }),
        now: () => now,
      );
      final pending = c.refresh();
      c.dispose();
      barrier.complete();
      await pending;
      expect(c.snapshot, isNull);
      expect(c.canUseAi, isFalse);
    },
  );

  test(
    'cancellation reflects server period end and preserves authorized access',
    () async {
      var canceled = false;
      final c = SubscriptionController(
        api: subscriptionApi((r) async {
          if (r.url.path.endsWith('cancel')) {
            canceled = true;
            return jsonResponse({}, 202);
          }
          return jsonResponse(
            r.url.path.endsWith('plans')
                ? planFixture
                : subscriptionFixture(tier: 'PLUS', ai: true, cancel: canceled),
          );
        }),
        now: () => now,
      );
      addTearDown(c.dispose);
      await c.refresh();
      await c.cancelAtPeriodEnd();
      expect(c.snapshot?.cancelAtPeriodEnd, isTrue);
      expect(c.snapshot?.tier, 'PLUS');
      expect(c.snapshot?.currentPeriodEnd, DateTime.utc(2030, 12, 12, 12));
      expect(c.canUseAi, isTrue);
    },
  );

  test('AI revalidates before POST and rejects a revoked grant', () async {
    var allowed = true;
    var aiCalls = 0;
    final c = SubscriptionController(
      api: subscriptionApi((r) async {
        if (r.url.path == '/api/v1/ai/generations') {
          aiCalls++;
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
    addTearDown(c.dispose);
    await c.refresh();
    expect((await c.generateAi('Một khu vườn'))?.id, 7);
    allowed = false;
    expect(await c.generateAi('Một khu vườn'), isNull);
    expect(aiCalls, 1);
    expect(c.canUseAi, isFalse);
  });

  test(
    'an older failed payment cannot resolve a new pending checkout',
    () async {
      final c = SubscriptionController(
        api: subscriptionApi(
          (r) async => jsonResponse(switch (r.url.path) {
            '/api/v1/subscriptions/plans' => planFixture,
            '/api/v1/subscriptions/checkout' => checkoutFixture(),
            _ => subscriptionFixture(
              payment: 'FAILED',
            )..['paymentSessionId'] = 'older-session',
          }),
        ),
        launchCheckout: (_) async => true,
        now: () => now,
      );
      addTearDown(c.dispose);
      await c.refresh();
      await c.startCheckout();
      await c.reconcileCheckout();
      expect(c.paymentPending, isTrue);
      expect(c.canUseAi, isFalse);
    },
  );

  testWidgets('delayed payment polling stops after four follow-up attempts', (
    t,
  ) async {
    var reads = 0;
    final c = SubscriptionController(
      api: subscriptionApi((r) async {
        if (r.url.path.endsWith('/me')) reads++;
        return jsonResponse(
          r.url.path.endsWith('plans')
              ? planFixture
              : subscriptionFixture(payment: 'PENDING'),
        );
      }),
      now: () => now,
    );
    await c.reconcileCheckout();
    for (var i = 0; i < 7; i++) {
      await t.pump(const Duration(seconds: 3));
      await t.pump();
    }
    expect(reads, 5);
    expect(c.paymentPending, isTrue);
    expect(c.refreshing, isFalse);
    c.dispose();
  });

  test('catalog cannot enable non-sandbox checkout', () async {
    var creates = 0;
    final c = SubscriptionController(
      api: subscriptionApi((r) async {
        if (r.url.path.endsWith('checkout')) creates++;
        return jsonResponse(
          r.url.path.endsWith('plans')
              ? {
                  'items': [
                    planFixture['items']!.first,
                    {...planFixture['items']!.last, 'sandbox': false},
                  ],
                }
              : subscriptionFixture(),
        );
      }),
      now: () => now,
    );
    addTearDown(c.dispose);
    await c.refresh();
    await c.startCheckout();
    expect(c.canCheckout, isFalse);
    expect(creates, 0);
  });

  test(
    'older failed snapshot cannot erase an uncertain checkout request UUID',
    () async {
      final ids = <String>[];
      final c = SubscriptionController(
        api: subscriptionApi((r) async {
          if (r.url.path.endsWith('/checkout')) {
            ids.add(jsonDecode(r.body)['clientRequestId'] as String);
            return ids.length == 1
                ? jsonResponse({}, 503)
                : jsonResponse(checkoutFixture());
          }
          return jsonResponse(
            r.url.path.endsWith('/plans')
                ? planFixture
                : (subscriptionFixture(payment: 'FAILED')
                    ..['paymentSessionId'] = 'old-payment'),
          );
        }),
        launchCheckout: (_) async => true,
        now: () => now,
      );
      addTearDown(c.dispose);
      await c.refresh();
      await c.startCheckout();
      await c.refresh();
      await c.startCheckout();
      expect(ids.length, 2);
      expect(ids[1], ids[0]);
    },
  );

  test('410 expiry resets request identity while 503 preserves it', () async {
    final ids = <String>[];
    final c = SubscriptionController(
      api: subscriptionApi((r) async {
        if (r.url.path.endsWith('/checkout')) {
          ids.add(jsonDecode(r.body)['clientRequestId'] as String);
          return switch (ids.length) {
            1 => jsonResponse({}, 503),
            2 => jsonResponse({'code': 'CHECKOUT_EXPIRED'}, 410),
            _ => jsonResponse(checkoutFixture()),
          };
        }
        return jsonResponse(
          r.url.path.endsWith('/plans') ? planFixture : subscriptionFixture(),
        );
      }),
      launchCheckout: (_) async => true,
      now: () => now,
    );
    addTearDown(c.dispose);
    await c.refresh();
    await c.startCheckout();
    await c.startCheckout();
    await c.startCheckout();
    expect(ids.length, 3);
    expect(ids[1], ids[0]);
    expect(ids[2], isNot(ids[1]));
    expect(c.error, isNull);
  });
}
