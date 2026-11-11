import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/subscription/subscription_api.dart';

import 'support/subscription_fixtures.dart';

void main() {
  test(
    'catalog and account snapshot use authenticated subscription endpoints',
    () async {
      final api = subscriptionApi((r) async {
        expect(r.headers['authorization'], 'Bearer test-user-token');
        expect(r.method, 'GET');
        return switch (r.url.path) {
          '/api/v1/subscriptions/plans' => jsonResponse(planFixture),
          '/api/v1/subscriptions/me' => jsonResponse(
            subscriptionFixture(tier: 'PLUS', ai: false, payment: 'PENDING'),
          ),
          _ => throw StateError('Unexpected ${r.url}'),
        };
      });
      final plans = await api.plans();
      expect(plans.map((p) => p.tier), ['FREE', 'PLUS']);
      expect(plans.last.priceLabel, '49.000 ₫ / tháng');
      expect(plans.last.sandbox, isTrue);
      final state = await api.current();
      expect(state.tier, 'PLUS');
      expect(state.aiGeneration, isFalse);
      expect(state.paymentStatus, 'PENDING');
    },
  );

  test(
    'checkout sends stable request identity and cancel schedules period end',
    () async {
      final api = subscriptionApi((r) async {
        expect(r.method, 'POST');
        if (r.url.path == '/api/v1/subscriptions/checkout') {
          expect(jsonDecode(r.body), {
            'tier': 'PLUS',
            'clientRequestId': 'fixed-uuid',
          });
          return jsonResponse(checkoutFixture(), 201);
        }
        expect(r.url.path, '/api/v1/subscriptions/me/cancel');
        expect(jsonDecode(r.body), {'cancelAtPeriodEnd': true});
        return jsonResponse({}, 202);
      });
      expect((await api.checkout('fixed-uuid')).sessionId, 'test-1');
      await api.cancelAtPeriodEnd();
    },
  );

  for (final change in <String, dynamic>{
    'tier': 'ADMIN',
    'status': 'UNKNOWN',
    'paymentStatus': 'UNKNOWN',
    'entitlements': {},
    'cancelAtPeriodEnd': 'yes',
    'validUntil': 'not-a-date',
    'currentPeriodEnd': 'invalid',
  }.entries) {
    test(
      'rejects malformed snapshot ${change.key} rather than granting access',
      () async {
        final data = subscriptionFixture(tier: 'PLUS', ai: true)
          ..[change.key] = change.value;
        await expectLater(
          subscriptionApi((_) async => jsonResponse(data)).current(),
          throwsA(isA<SubscriptionException>()),
        );
      },
    );
  }

  test('rejects missing or duplicate plan tiers and blank prices', () async {
    for (final data in [
      {'items': []},
      {
        'items': [planFixture['items']!.first, planFixture['items']!.first],
      },
      {
        'items': [
          planFixture['items']!.first,
          {...planFixture['items']!.last, 'priceLabel': ''},
        ],
      },
    ]) {
      await expectLater(
        subscriptionApi((_) async => jsonResponse(data)).plans(),
        throwsA(isA<SubscriptionException>()),
      );
    }
  });

  for (final url in [
    'http://checkout.test',
    'javascript:alert(1)',
    'https://user:pass@checkout.test',
    'https:///',
  ]) {
    test('does not launch unsafe checkout URL $url', () async {
      await expectLater(
        subscriptionApi((_) async => jsonResponse(checkoutFixture(url: url)))
            .checkout('id'),
        throwsA(isA<SubscriptionException>()),
      );
    });
  }

  test('rejects a non-sandbox checkout session', () async {
    await expectLater(
      subscriptionApi(
        (_) async => jsonResponse(checkoutFixture(sandbox: false)),
      ).checkout('id'),
      throwsA(isA<SubscriptionException>()),
    );
  });

  test(
    'unavailable backend is a typed failure and does not manufacture Free',
    () async {
      await expectLater(
        subscriptionApi((_) async => jsonResponse({'code': 'NOT_FOUND'}, 404))
            .current(),
        throwsA(
          isA<SubscriptionException>().having(
            (e) => e.statusCode,
            'status',
            404,
          ),
        ),
      );
    },
  );

  test('null catalog success is a typed invalid response', () async {
    await expectLater(
      subscriptionApi((_) async => jsonResponse(null)).plans(),
      throwsA(isA<SubscriptionException>()),
    );
  });

  test(
    'existing AI API sends text-to-image payload and parses job status',
    () async {
      final api = subscriptionApi((r) async {
        if (r.method == 'POST') {
          expect(r.url.path, '/api/v1/ai/generations');
          expect(jsonDecode(r.body), {
            'stylePrompt': 'Một khu vườn',
            'generationType': 'TEXT_TO_IMAGE',
          });
        } else {
          expect(r.url.path, '/api/v1/ai/generations/7');
        }
        return jsonResponse({
          'id': 7,
          'status': r.method == 'POST' ? 'PENDING' : 'SUCCESS',
          'generatedMediaUrl': r.method == 'POST'
              ? null
              : 'https://media.test/result.png',
        }, 200);
      });
      expect((await api.createAi('Một khu vườn')).status, 'PENDING');
      expect(
        (await api.aiGeneration(7)).mediaUrl?.toString(),
        'https://media.test/result.png',
      );
    },
  );
}
