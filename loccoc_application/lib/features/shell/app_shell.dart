import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../auth/auth_controller.dart';
import '../../core/models/post_models.dart';
import '../../core/network/posts_api.dart';
import '../composer/composer_screen.dart';
import '../feed/feed_screen.dart';
import '../feed/feed_controller.dart';
import '../friends/friends_controller.dart';
import '../friends/screens/friends_screen.dart';
import '../profile/screens/profile_screen.dart';
import '../../core/config/app_config.dart';
import '../chat/chat_screen.dart';
import '../notifications/notification_center_screen.dart';
import '../s3/notification_launch_controller.dart';
import '../s3/notification_banner_overlay.dart';
import '../s3/s3_session.dart';
import '../s3/s3_target_screen.dart';
import '../feed/media_detail_screen.dart';

class AppShell extends StatefulWidget {
  final AuthController authController;

  final S3Session? Function(int accountId)? s3SessionFactory;
  final NotificationLaunchController? notificationLaunches;
  final PostsApi? postsApi;
  final FeedController? feedController;
  final Widget Function(PostsApi api, int accountId)? composerBuilder;
  const AppShell({
    super.key,
    required this.authController,
    this.postsApi,
    this.s3SessionFactory,
    this.notificationLaunches,
    this.feedController,
    this.composerBuilder,
  });

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> with WidgetsBindingObserver {
  int _currentIndex = 0;
  final _feedKey = GlobalKey<FeedScreenState>();
  late final PostsApi _postsApi;
  int? _accountId;
  Set<int>? _friendIds;
  late FriendsController _friendsController;
  S3Session? _s3;
  NotificationBannerOverlay? _bannerOverlay;
  late final NotificationLaunchController _launches;
  bool _s3Ready = false;
  bool _launchScheduled = false;
  bool _foreground = true;

  @override
  void initState() {
    super.initState();
    _accountId = widget.authController.currentUser?.id;
    _postsApi = widget.postsApi ?? PostsApi(widget.authController.apiClient);
    _friendsController = FriendsController(
      api: widget.authController.apiClient,
    );
    _friendsController.addListener(_friendsChanged);
    widget.authController.addListener(_sessionChanged);
    WidgetsBinding.instance.addObserver(this);
    _foreground =
        WidgetsBinding.instance.lifecycleState == null ||
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    _launches = widget.notificationLaunches ?? NotificationLaunchController();
    _launches.addListener(_scheduleLaunch);
    _createS3();
  }

  void _createS3() {
    final id = _accountId;
    _s3Ready = false;
    if (id == null || id <= 0) return;
    _s3 = widget.s3SessionFactory != null
        ? widget.s3SessionFactory!(id)
        : S3Session.forEnvironment(
            accountId: id,
            environment: AppConfig.environment,
            demoEnabled: const bool.fromEnvironment(
              'S3_DEMO',
              defaultValue: true,
            ),
          );
    final session = _s3;
    if (session == null) return;
    session.chat.setForeground(_foreground);
    session.notifications.addListener(_notificationsChanged);
    unawaited(_initializeS3(session));
  }

  Future<void> _initializeS3(S3Session session) async {
    try {
      await session.initialize();
    } catch (_) {
      // Domain controllers retain loading/storage errors and offer retry.
    }
    if (!mounted || !identical(session, _s3)) return;
    _bannerOverlay = NotificationBannerOverlay(
      controller: session.notifications,
      onOpen: _openTarget,
    )..attach(Navigator.of(context).overlay!);
    _bannerOverlay!.setForeground(_foreground);
    setState(() => _s3Ready = true);
    _scheduleLaunch();
  }

  void _notificationsChanged() {
    if (mounted) setState(() {});
  }

  void _disposeS3() {
    _bannerOverlay?.dispose();
    _bannerOverlay = null;
    _s3?.notifications.removeListener(_notificationsChanged);
    _s3?.dispose();
    _s3 = null;
    _s3Ready = false;
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    _s3?.chat.setForeground(_foreground);
    _bannerOverlay?.setForeground(_foreground);
    if (_foreground) {
      unawaited(_s3?.notifications.refresh());
      _scheduleLaunch();
    }
    if (mounted) setState(() {});
  }

  void _scheduleLaunch() {
    if (!_s3Ready || !_foreground || _launchScheduled || !mounted) return;
    _launchScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _launchScheduled = false;
      if (!mounted || !_s3Ready || !_foreground) return;
      final id = _accountId;
      if (id == null) return;
      final launch = _launches.takeForAccount(id);
      if (launch != null) {
        final notificationId = launch.notificationId;
        if (notificationId != null) {
          unawaited(_s3?.notifications.markReadById(notificationId));
        }
        unawaited(_openTarget(launch.target));
      }
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  Future<void> _openTarget(NotificationTarget target) async {
    final session = _s3;
    if (!mounted || session == null || session.accountId != _accountId) return;
    Widget destination;
    if (target.kind == NotificationTargetKind.conversation &&
        target.id != null) {
      destination = ChatRoomScreen(
        controller: session.chat,
        conversationId: target.id!,
        focusMessageId: target.messageId,
      );
    } else if (session.isDemo) {
      destination = S3DemoTargetScreen(target: target);
    } else if (target.kind == NotificationTargetKind.post &&
        target.id != null) {
      final account = _accountId;
      destination = MediaDetailScreen(
        api: _postsApi,
        postId: target.id!,
        onUnavailable: (_) {},
        isSessionActive: () => mounted && account == _accountId,
        accessChanges: widget.authController,
      );
    } else {
      destination = const S3UnavailableTargetScreen();
    }
    await Navigator.of(context)
        .push<void>(MaterialPageRoute(builder: (_) => destination));
  }

  Future<void> _openNotifications() async {
    final session = _s3;
    if (session == null) return;
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => Scaffold(
          appBar: AppBar(title: const Text('Thông báo')),
          body: NotificationCenterScreen(
            controller: session.notifications,
            onOpen: _openTarget,
          ),
        ),
      ),
    );
  }

  void _friendsChanged() {
    if (!_friendsController.hasLoadedFriends) return;
    final ids = _friendsController.friends.map((f) => f.id).toSet();
    if (_friendIds != null && setEquals(_friendIds, ids)) return;
    _friendIds = ids;
    unawaited(_feedKey.currentState?.invalidateFriendships(ids));
  }

  void _sessionChanged() {
    final nextId = widget.authController.status == AuthStatus.authenticated
        ? widget.authController.currentUser?.id
        : null;
    if (nextId == _accountId &&
        widget.authController.status == AuthStatus.authenticated) {
      return;
    }
    unawaited(_feedKey.currentState?.invalidateSession());
    _launches.clearPending();
    _disposeS3();
    if (mounted) {
      Navigator.of(context).popUntil((route) => route.isFirst);
      if (nextId != _accountId) {
        _friendsController.removeListener(_friendsChanged);
        _friendsController.dispose();
        _friendsController = FriendsController(
          api: widget.authController.apiClient,
        )..addListener(_friendsChanged);
        setState(() {
          _accountId = nextId;
          _friendIds = null;
          _currentIndex = 0;
          _createS3();
        });
      }
    }
  }

  Future<void> _compose() async {
    final id = _accountId;
    if (id == null || id <= 0) return;
    final post = await Navigator.of(context).push<PostModel>(
      MaterialPageRoute(
        builder: (_) =>
            widget.composerBuilder?.call(_postsApi, id) ??
            ComposerScreen(api: _postsApi, accountId: id),
      ),
    );
    if (!mounted ||
        widget.authController.currentUser?.id != id ||
        post == null) {
      return;
    }
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('Đã đăng ảnh thành công.')));
    await _feedKey.currentState?.refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _launches.removeListener(_scheduleLaunch);
    if (widget.notificationLaunches == null) _launches.dispose();
    _disposeS3();
    widget.authController.removeListener(_sessionChanged);
    _friendsController.removeListener(_friendsChanged);
    _friendsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final screens = [
      _buildFeedTab(),
      FriendsScreen(key: ValueKey(_accountId), controller: _friendsController),
      _buildChatTab(),
      ProfileScreen(authController: widget.authController),
    ];

    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            if (_s3 != null) _buildS3Header(),
            Expanded(
              child: IndexedStack(index: _currentIndex, children: screens),
            ),
          ],
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (index) {
          setState(() => _currentIndex = index);
        },
        backgroundColor: Colors.white,
        indicatorColor: const Color(0xFFDCE6F1),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.dynamic_feed_outlined),
            selectedIcon: Icon(Icons.dynamic_feed, color: Color(0xFF17365D)),
            label: 'Bảng tin',
          ),
          NavigationDestination(
            icon: Icon(Icons.people_outline),
            selectedIcon: Icon(Icons.people, color: Color(0xFF17365D)),
            label: 'Bạn bè',
          ),
          NavigationDestination(
            icon: Icon(Icons.chat_bubble_outline),
            selectedIcon: Icon(Icons.chat_bubble, color: Color(0xFF17365D)),
            label: 'Tin nhắn',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person, color: Color(0xFF17365D)),
            label: 'Cá nhân',
          ),
        ],
      ),
    );
  }

  Widget _buildFeedTab() {
    final id = _accountId;
    if (id == null || id <= 0) {
      return const Center(
        child: Text('Vui lòng đăng nhập lại để xem bảng tin.'),
      );
    }
    return FeedScreen(
      key: _feedKey,
      api: _postsApi,
      accountId: id,
      onCompose: _compose,
      controller: widget.feedController,
    );
  }

  Widget _buildS3Header() {
    final session = _s3!;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Material(
          color: const Color(0xFFEAF0F7),
          child: Padding(
            padding: const EdgeInsets.only(left: 16, right: 8),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    session.isDemo ? 'S3 · Dữ liệu mô phỏng' : 'LocCoc',
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
                IconButton(
                  tooltip: 'Thông báo',
                  onPressed: _openNotifications,
                  icon: Badge(
                    key: const Key('s3-unread-badge'),
                    isLabelVisible: session.notifications.unreadCount > 0,
                    label: Text(
                      session.notifications.unreadCount > 99
                          ? '99+'
                          : '${session.notifications.unreadCount}',
                    ),
                    child: const Icon(Icons.notifications_outlined),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildChatTab() {
    final session = _s3;
    if (session != null) {
      return ConversationListScreen(
        controller: session.chat,
        active: _currentIndex == 2,
      );
    }
    return Scaffold(
      appBar: AppBar(title: const Text('Tin nhắn')),
      body: const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'Tin nhắn sẽ sẵn sàng khi dịch vụ được kết nối.',
            textAlign: TextAlign.center,
          ),
        ),
      ),
    );
  }
}
