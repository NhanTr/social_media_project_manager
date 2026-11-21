/// Device adapters implement this boundary after the app's explanation is accepted.
enum NotificationPermissionResult { granted, denied, simulated, unavailable }

abstract interface class NotificationPermissionAdapter {
  Future<NotificationPermissionResult> request();
}

/// No OS request, token registration, or real push is performed in demo mode.
class DemoNotificationPermissionAdapter
    implements NotificationPermissionAdapter {
  @override
  Future<NotificationPermissionResult> request() async =>
      NotificationPermissionResult.simulated;
}
