import 'package:flutter/foundation.dart';

import '../notifications/notification_models.dart';

/// Adapter entry for notification taps, including a tap before login completes.
/// A platform push adapter can offer its payload here when it is connected later.
class NotificationLaunchController extends ChangeNotifier {
  NotificationLaunchController({Map<String, dynamic>? initialPayload}) {
    if (initialPayload != null) {
      _pending = NotificationPushPayload.parse(initialPayload);
    }
  }

  NotificationPushPayload? _pending;
  int? _recipientAccountId;
  final Set<String> _consumed = {};
  bool get hasPending => _pending != null;

  void offer(Map<String, dynamic> payload, {int? recipientAccountId}) {
    _pending = NotificationPushPayload.parse(payload);
    _recipientAccountId = recipientAccountId;
    notifyListeners();
  }

  NotificationPushPayload? takeForAccount(int accountId) {
    if (accountId <= 0) return null;
    final value = _pending;
    _pending = null;
    final recipient = _recipientAccountId;
    _recipientAccountId = null;
    if (value == null || recipient != null && recipient != accountId) {
      return null;
    }
    final notificationId = value.notificationId;
    if (notificationId != null) {
      final key = '$accountId:$notificationId';
      if (!_consumed.add(key)) return null;
      if (_consumed.length > 100) _consumed.remove(_consumed.first);
    }
    return value;
  }

  /// Clear unconsumed taps when leaving a session. A push cannot choose a user.
  void clearPending() {
    _pending = null;
    _recipientAccountId = null;
  }
}
