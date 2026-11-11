String requiredText(dynamic value) {
  if (value is! String || value.trim().isEmpty) {
    throw const FormatException('Missing text');
  }
  return value;
}

String _state(dynamic value, Set<String> allowed) {
  if (value is! String || !allowed.contains(value)) {
    throw const FormatException('Unknown state');
  }
  return value;
}

DateTime serverTime(dynamic value) {
  final text = requiredText(value);
  if (!RegExp(r'(Z|[+-]\d{2}:\d{2})$').hasMatch(text)) {
    throw const FormatException('Timestamp needs timezone');
  }
  return DateTime.parse(text).toUtc();
}

Uri secureWebUri(dynamic value) {
  final uri = Uri.parse(requiredText(value));
  if (uri.scheme != 'https' || uri.host.isEmpty || uri.userInfo.isNotEmpty) {
    throw const FormatException('Expected secure web URL');
  }
  return uri;
}

class SubscriptionPlan {
  final String tier;
  final String name;
  final String priceLabel;
  final bool sandbox;
  final bool checkoutEnabled;
  final List<String> benefits;

  SubscriptionPlan.fromJson(Map<String, dynamic> json)
    : tier = _state(json['tier'], {'FREE', 'PLUS'}),
      name = requiredText(json['name']),
      priceLabel = requiredText(json['priceLabel']),
      sandbox = json['sandbox'] as bool,
      checkoutEnabled = json['checkoutEnabled'] as bool,
      benefits = List.unmodifiable(
        (json['benefits'] as List).map(requiredText),
      );
}

class SubscriptionSnapshot {
  final String tier;
  final String status;
  final String paymentStatus;
  final String? paymentSessionId;
  final bool cancelAtPeriodEnd;
  final DateTime? currentPeriodEnd;
  final DateTime validUntil;
  final bool aiGeneration;

  SubscriptionSnapshot.fromJson(Map<String, dynamic> json)
    : tier = _state(json['tier'], {'FREE', 'PLUS'}),
      status = _state(json['status'], {
        'ACTIVE',
        'GRACE',
        'PAST_DUE',
        'CANCELED',
        'EXPIRED',
        'INCOMPLETE',
      }),
      paymentStatus = _state(json['paymentStatus'], {
        'NONE',
        'PENDING',
        'SUCCEEDED',
        'FAILED',
      }),
      paymentSessionId = json['paymentSessionId'] == null
          ? null
          : requiredText(json['paymentSessionId']),
      cancelAtPeriodEnd = json['cancelAtPeriodEnd'] as bool,
      currentPeriodEnd = json['currentPeriodEnd'] == null
          ? null
          : serverTime(json['currentPeriodEnd']),
      validUntil = serverTime(json['validUntil']),
      aiGeneration =
          (json['entitlements'] as Map<String, dynamic>)['aiGeneration']
              as bool {
    if (paymentStatus != 'NONE' && paymentSessionId == null) {
      throw const FormatException('Payment state requires session correlation');
    }
  }
}

class CheckoutSession {
  final String sessionId;
  final Uri url;
  final DateTime expiresAt;

  CheckoutSession.fromJson(Map<String, dynamic> json)
    : sessionId = requiredText(json['sessionId']),
      url = secureWebUri(json['url']),
      expiresAt = serverTime(json['expiresAt']) {
    if (json['sandbox'] != true) {
      throw const FormatException('Only sandbox checkout is supported');
    }
  }
}

class AiGeneration {
  final int id;
  final String status;
  final Uri? mediaUrl;

  AiGeneration.fromJson(Map<String, dynamic> json)
    : id = json['id'] as int,
      status = _state(json['status'], {
        'PENDING',
        'PROCESSING',
        'SUCCESS',
        'FAILED',
      }),
      mediaUrl = json['generatedMediaUrl'] == null
          ? null
          : secureWebUri(json['generatedMediaUrl']) {
    if (id <= 0 || (status == 'SUCCESS' && mediaUrl == null)) {
      throw const FormatException('Invalid generation');
    }
  }
}
