import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:loccoc_application/features/subscription/subscription_controller.dart';
import 'package:loccoc_application/features/subscription/subscription_screen.dart';

import '../test/support/subscription_fixtures.dart';

// Native rendering/state acceptance using HTTP fixtures. This is not evidence
// that the absent payment/subscription backend or provider webhook works.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('native paywall checkout return refresh and cancellation', (
    t,
  ) async {
    var paid = false;
    var canceled = false;
    Uri? launched;
    final c = SubscriptionController(
      api: subscriptionApi((r) async {
        if (r.url.path.endsWith('/checkout')) {
          return jsonResponse(checkoutFixture());
        }
        if (r.url.path.endsWith('/cancel')) {
          canceled = true;
          return jsonResponse({}, 202);
        }
        return jsonResponse(
          r.url.path.endsWith('/plans')
              ? planFixture
              : subscriptionFixture(
                  tier: paid ? 'PLUS' : 'FREE',
                  ai: paid,
                  cancel: canceled,
                  payment: paid ? 'SUCCEEDED' : 'PENDING',
                ),
        );
      }),
      launchCheckout: (uri) async {
        launched = uri;
        return true;
      },
      now: () => DateTime.utc(2030, 11, 12, 12),
    );
    await t.pumpWidget(MaterialApp(home: SubscriptionScreen(controller: c)));
    await t.pumpAndSettle();
    expect(find.text('Gói hiện tại: Free'), findsOneWidget);
    await t.scrollUntilVisible(find.byKey(const Key('checkout')), 250);
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('checkout')));
    await t.pumpAndSettle();
    expect(launched?.scheme, 'https');
    expect(c.canUseAi, isFalse);
    await c.reconcileCheckout();
    expect(c.canUseAi, isFalse);
    paid = true;
    await c.refresh();
    await t.pumpAndSettle();
    expect(c.canUseAi, isTrue);
    await t.scrollUntilVisible(find.byKey(const Key('cancel-renewal')), 200);
    await t.pumpAndSettle();
    await t.ensureVisible(find.byKey(const Key('cancel-renewal')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('cancel-renewal')));
    await t.pumpAndSettle();
    expect(canceled, isFalse);
    await t.tap(find.text('Xác nhận hủy'));
    await t.pumpAndSettle();
    expect(canceled, isTrue);
    expect(c.canUseAi, isTrue);
    await t.drag(find.byType(ListView), const Offset(0, 2400));
    await t.pumpAndSettle();
    expect(find.text('Gói hiện tại: Plus'), findsOneWidget);
    expect(find.textContaining('Đã hủy gia hạn'), findsOneWidget);
    debugPrint('S4_NATIVE_CAPTURE_READY');
    await Future<void>.delayed(const Duration(seconds: 5));
    await t.pumpWidget(const SizedBox());
    c.dispose();
  });
}
