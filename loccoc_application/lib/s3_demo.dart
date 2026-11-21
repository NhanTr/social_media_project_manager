import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';

import 'core/config/app_config.dart';
import 'features/chat/chat_screen.dart';
import 'features/notifications/notification_center_screen.dart';
import 'features/s3/notification_launch_controller.dart';
import 'features/s3/notification_banner_overlay.dart';
import 'features/s3/s3_session.dart';
import 'features/s3/s3_target_screen.dart';

/// Standalone preview: no backend login, auth bypass, network calls or real push.
/// Run: flutter run -t lib/s3_demo.dart --dart-define=APP_ENV=dev
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  if (AppConfig.environment != 'dev') {
    throw StateError('Bản xem trước S3 chỉ chạy với APP_ENV=dev.');
  }
  Map<String, dynamic>? initialPayload;
  const raw = String.fromEnvironment('S3_DEMO_INITIAL_PUSH');
  if (raw.isNotEmpty) {
    try {
      initialPayload = Map<String, dynamic>.from(jsonDecode(raw) as Map);
    } catch (_) {
      initialPayload = {};
    }
  }
  runApp(S3DemoApp(initialPayload: initialPayload));
}

class S3DemoApp extends StatefulWidget {
  const S3DemoApp({super.key, this.initialPayload, this.sessionFactory});
  final Map<String, dynamic>? initialPayload;
  final S3Session Function()? sessionFactory;
  @override
  State<S3DemoApp> createState() => _S3DemoAppState();
}

class _S3DemoAppState extends State<S3DemoApp> with WidgetsBindingObserver {
  late final S3Session _session;
  late final NotificationLaunchController _launches;
  final _navigator = GlobalKey<NavigatorState>();
  NotificationBannerOverlay? _bannerOverlay;
  int _tab = 0;
  bool _ready = false;
  bool _foreground = true;

  @override
  void initState() {
    super.initState();
    _session =
        widget.sessionFactory?.call() ??
        S3Session.forEnvironment(
          accountId: 1,
          environment: AppConfig.environment,
        )!;
    _launches = NotificationLaunchController(
      initialPayload: widget.initialPayload,
    );
    WidgetsBinding.instance.addObserver(this);
    _session.notifications.addListener(_changed);
    unawaited(_initialize());
  }

  Future<void> _initialize() async {
    try {
      await _session.initialize();
    } catch (_) {
      // The domain screens show retryable loading/storage failures.
    }
    if (!mounted) return;
    _bannerOverlay = NotificationBannerOverlay(
      controller: _session.notifications,
      onOpen: _open,
    )..attach(_navigator.currentState!.overlay!);
    _bannerOverlay!.setForeground(_foreground);
    setState(() => _ready = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final initial = _launches.takeForAccount(_session.accountId);
      if (initial != null) {
        final id = initial.notificationId;
        if (id != null) unawaited(_session.notifications.markReadById(id));
        unawaited(_open(initial.target));
      }
    });
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    _session.chat.setForeground(_foreground);
    _bannerOverlay?.setForeground(_foreground);
    if (_foreground) unawaited(_session.notifications.refresh());
    if (mounted) setState(() {});
  }

  Future<void> _open(NotificationTarget target) async {
    if (!mounted || !_ready) return;
    final destination =
        target.kind == NotificationTargetKind.conversation && target.id != null
        ? ChatRoomScreen(
            controller: _session.chat,
            conversationId: target.id!,
            focusMessageId: target.messageId,
          )
        : S3DemoTargetScreen(target: target);
    await _navigator.currentState?.push<void>(
      MaterialPageRoute(builder: (_) => destination),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _bannerOverlay?.dispose();
    _session.notifications.removeListener(_changed);
    _session.dispose();
    _launches.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: _navigator,
      navigatorObservers: [chatRouteObserver],
      debugShowCheckedModeBanner: false,
      title: 'LocCoc · S3 Preview',
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: const Color(0xFF17365D),
      ),
      home: Scaffold(
        body: SafeArea(
          child: Column(
            children: [
              Material(
                color: const Color(0xFFEAF0F7),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 10,
                  ),
                  child: Row(
                    children: [
                      const Expanded(
                        child: Text('LocCoc S3 · Dữ liệu mô phỏng'),
                      ),
                      Badge(
                        isLabelVisible: _session.notifications.unreadCount > 0,
                        label: Text('${_session.notifications.unreadCount}'),
                        child: const Icon(Icons.notifications_outlined),
                      ),
                    ],
                  ),
                ),
              ),
              Expanded(
                child: !_ready
                    ? const Center(child: CircularProgressIndicator())
                    : IndexedStack(
                        index: _tab,
                        children: [
                          ConversationListScreen(
                            controller: _session.chat,
                            active: _tab == 0,
                          ),
                          NotificationCenterScreen(
                            controller: _session.notifications,
                            onOpen: _open,
                          ),
                        ],
                      ),
              ),
            ],
          ),
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _tab,
          onDestinationSelected: (value) => setState(() => _tab = value),
          destinations: const [
            NavigationDestination(
              icon: Icon(Icons.chat_bubble_outline),
              label: 'Tin nhắn',
            ),
            NavigationDestination(
              icon: Icon(Icons.notifications_outlined),
              label: 'Thông báo',
            ),
          ],
        ),
      ),
    );
  }
}
