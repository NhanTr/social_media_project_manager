import '../../core/storage/post_local_store.dart';
import '../chat/chat_controller.dart';
import '../chat/demo_chat_repository.dart';
import '../notifications/notification_controller.dart';
import '../notifications/notification_repository.dart';

/// Composition boundary. Future API/STOMP/push adapters implement the domain
/// repositories; the screens and durable queue do not depend on HTTP clients.
class S3Session {
  S3Session({
    required this.accountId,
    required this.chat,
    required this.notifications,
    required this.isDemo,
  });

  final int accountId;
  final ChatController chat;
  final NotificationController notifications;
  final bool isDemo;
  Future<void>? _initialization;
  bool _disposed = false;

  static S3Session? forEnvironment({
    required int accountId,
    required String environment,
    bool demoEnabled = true,
    String? directory,
  }) {
    if (environment != 'dev' || !demoEnabled) return null;
    if (accountId <= 0) throw ArgumentError.value(accountId, 'accountId');
    final chatStore = PostLocalStore(
      accountId: accountId,
      directory: directory,
      namespace: 's3-chat-demo',
    );
    final notificationStore = PostLocalStore(
      accountId: accountId,
      directory: directory,
      namespace: 's3-notifications-demo',
    );
    return S3Session(
      accountId: accountId,
      isDemo: true,
      chat: ChatController(
        accountId: accountId,
        repository: DemoChatRepository(accountId: accountId, store: chatStore),
        store: chatStore,
      ),
      notifications: NotificationController(
        accountId: accountId,
        repository: DemoNotificationRepository(
          accountId: accountId,
          store: notificationStore,
        ),
      ),
    );
  }

  Future<void> initialize() {
    if (_disposed) return Future<void>.value();
    return _initialization ??= Future.wait([
      chat.initialize(),
      notifications.initialize(),
    ]).then((_) {});
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    chat.dispose();
    notifications.dispose();
  }
}
