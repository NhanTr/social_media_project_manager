import '../../core/network/api_client.dart';
import 'subscription_models.dart';

class SubscriptionException implements Exception {
  final int statusCode;
  final String code;
  const SubscriptionException(this.statusCode, this.code);

  @override
  String toString() => 'SubscriptionException($statusCode, $code)';
}

class SubscriptionApi {
  final ApiClient _api;
  SubscriptionApi(this._api);

  Future<T> _read<T>(
    Future<ApiResponse<dynamic>> request,
    T Function(dynamic) parse,
  ) async {
    final response = await request;
    if (!response.success) {
      throw SubscriptionException(
        response.statusCode,
        response.errorCode ?? 'UNAVAILABLE',
      );
    }
    try {
      return parse(response.data);
    } on FormatException {
      throw const SubscriptionException(502, 'INVALID_RESPONSE');
    } on TypeError {
      throw const SubscriptionException(502, 'INVALID_RESPONSE');
    }
  }

  Future<List<SubscriptionPlan>> plans() =>
      _read(_api.get('/api/v1/subscriptions/plans'), (data) {
        final json = data as Map<String, dynamic>;
        final plans = (json['items'] as List)
            .map((v) => SubscriptionPlan.fromJson(v as Map<String, dynamic>))
            .toList();
        if (plans.length != 2 || plans.map((p) => p.tier).toSet().length != 2) {
          throw const FormatException('Expected FREE and PLUS catalog');
        }
        plans.sort((a, b) => a.tier.compareTo(b.tier));
        return List.unmodifiable(plans);
      });

  Future<SubscriptionSnapshot> current() => _read(
    _api.get('/api/v1/subscriptions/me'),
    (data) => SubscriptionSnapshot.fromJson(data as Map<String, dynamic>),
  );

  Future<CheckoutSession> checkout(String clientRequestId) => _read(
    _api.post(
      '/api/v1/subscriptions/checkout',
      body: {'tier': 'PLUS', 'clientRequestId': clientRequestId},
    ),
    (data) => CheckoutSession.fromJson(data as Map<String, dynamic>),
  );

  Future<void> cancelAtPeriodEnd() => _read(
    _api.post(
      '/api/v1/subscriptions/me/cancel',
      body: {'cancelAtPeriodEnd': true},
    ),
    (_) {},
  );

  Future<AiGeneration> createAi(String prompt) => _read(
    _api.post(
      '/api/v1/ai/generations',
      body: {'stylePrompt': prompt.trim(), 'generationType': 'TEXT_TO_IMAGE'},
    ),
    (data) => AiGeneration.fromJson(data as Map<String, dynamic>),
  );

  Future<AiGeneration> aiGeneration(int id) => _read(
    _api.get('/api/v1/ai/generations/$id'),
    (data) => AiGeneration.fromJson(data as Map<String, dynamic>),
  );
}
