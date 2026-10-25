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

class AppShell extends StatefulWidget {
  final AuthController authController;

  final PostsApi? postsApi;
  final FeedController? feedController;
  final Widget Function(PostsApi api, int accountId)? composerBuilder;
  const AppShell({
    super.key,
    required this.authController,
    this.postsApi,
    this.feedController,
    this.composerBuilder,
  });

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  int _currentIndex = 0;
  final _feedKey = GlobalKey<FeedScreenState>();
  late final PostsApi _postsApi;
  late final int? _accountId;
  Set<int>? _friendIds;
  late FriendsController _friendsController;

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
  }

  void _friendsChanged() {
    if (!_friendsController.hasLoadedFriends) return;
    final ids = _friendsController.friends.map((f) => f.id).toSet();
    if (_friendIds != null && setEquals(_friendIds, ids)) return;
    _friendIds = ids;
    unawaited(_feedKey.currentState?.invalidateFriendships(ids));
  }

  void _sessionChanged() {
    if (widget.authController.currentUser?.id == _accountId &&
        widget.authController.status == AuthStatus.authenticated) {
      return;
    }
    unawaited(_feedKey.currentState?.invalidateSession());
    if (mounted) Navigator.of(context).popUntil((route) => route.isFirst);
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
    widget.authController.removeListener(_sessionChanged);
    _friendsController.removeListener(_friendsChanged);
    _friendsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final screens = [
      _buildFeedTab(),
      FriendsScreen(controller: _friendsController),
      _buildChatTab(),
      ProfileScreen(authController: widget.authController),
    ];

    return Scaffold(
      body: IndexedStack(index: _currentIndex, children: screens),
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

  Widget _buildChatTab() {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Tin nhắn',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        backgroundColor: const Color(0xFF17365D),
        foregroundColor: Colors.white,
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: const Color(0xFFF3F7FB),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.mark_chat_unread_outlined,
                  size: 64,
                  color: Color(0xFF17365D),
                ),
              ),
              const SizedBox(height: 16),
              const Text(
                'Nhắn tin Realtime 1-1',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF17365D),
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'Tính năng nhắn tin trực tiếp và thông báo đẩy đang được lên kế hoạch trong Sprint 3 (LC-S3-01 đến LC-S3-04).',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 14, color: Colors.black54),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
