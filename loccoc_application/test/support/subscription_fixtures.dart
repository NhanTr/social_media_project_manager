import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:loccoc_application/core/network/api_client.dart';
import 'package:loccoc_application/core/storage/session_storage.dart';
import 'package:loccoc_application/features/subscription/subscription_api.dart';

class SubscriptionSession extends SessionStorage {
  @override
  Future<String?> getAccessToken() async => 'test-user-token';
}

Map<String, dynamic> subscriptionFixture({
  String tier = 'FREE',
  bool ai = false,
  String payment = 'NONE',
  bool cancel = false,
  String status = 'ACTIVE',
  String validUntil = '2030-11-12T12:10:00Z',
}) => {
  'tier': tier,
  'status': status,
  'paymentStatus': payment,
  'paymentSessionId': payment == 'NONE' ? null : 'test-1',
  'cancelAtPeriodEnd': cancel,
  'currentPeriodEnd': tier == 'PLUS' ? '2030-12-12T12:00:00Z' : null,
  'validUntil': validUntil,
  'entitlements': {'aiGeneration': ai},
};

final planFixture = {
  'items': [
    {
      'tier': 'FREE',
      'name': 'Free',
      'priceLabel': 'Miễn phí',
      'sandbox': true,
      'checkoutEnabled': false,
      'benefits': ['Chia sẻ ảnh với bạn bè'],
    },
    {
      'tier': 'PLUS',
      'name': 'Plus',
      'priceLabel': '49.000 ₫ / tháng',
      'sandbox': true,
      'checkoutEnabled': true,
      'benefits': ['Tạo ảnh AI theo quyền được cấp'],
    },
  ],
};

Map<String, dynamic> checkoutFixture({
  String url = 'https://checkout.example.test/session/test-1',
  bool sandbox = true,
  String expiresAt = '2030-11-12T12:30:00Z',
}) => {
  'sessionId': 'test-1',
  'url': url,
  'sandbox': sandbox,
  'expiresAt': expiresAt,
};

SubscriptionApi subscriptionApi(
  Future<http.Response> Function(http.Request) handler,
) => SubscriptionApi(
  ApiClient(client: MockClient(handler), storage: SubscriptionSession()),
);

http.Response jsonResponse(Object? value, [int status = 200]) => http.Response(
  jsonEncode(value),
  status,
  headers: {'content-type': 'application/json; charset=utf-8'},
);
