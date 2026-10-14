import 'package:flutter/material.dart';
import '../auth/auth_controller.dart';
import '../friends/friends_controller.dart';
import '../friends/screens/friends_screen.dart';
import '../profile/screens/profile_screen.dart';

class AppShell extends StatefulWidget {
  final AuthController authController;

  const AppShell({super.key, required this.authController});

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  int _currentIndex = 1; // Default to Friends tab for Sprint 1 demo
  late FriendsController _friendsController;

  @override
  void initState() {
    super.initState();
    _friendsController = FriendsController(api: widget.authController.apiClient);
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
      body: IndexedStack(
        index: _currentIndex,
        children: screens,
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
    return Scaffold(
      appBar: AppBar(
        title: const Text('Bảng tin LocCoc', style: TextStyle(fontWeight: FontWeight.bold)),
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
                child: const Icon(Icons.photo_library_outlined, size: 64, color: Color(0xFF17365D)),
              ),
              const SizedBox(height: 16),
              const Text(
                'Bảng tin Bạn bè (Friends-Only Feed)',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Color(0xFF17365D)),
              ),
              const SizedBox(height: 8),
              const Text(
                'Tính năng chia sẻ ảnh, feed bạn bè và AI Generation đang được triển khai trong Sprint 2 (LC-S2-01 đến LC-S2-04).',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 14, color: Colors.black54),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildChatTab() {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Tin nhắn', style: TextStyle(fontWeight: FontWeight.bold)),
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
                child: const Icon(Icons.mark_chat_unread_outlined, size: 64, color: Color(0xFF17365D)),
              ),
              const SizedBox(height: 16),
              const Text(
                'Nhắn tin Realtime 1-1',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Color(0xFF17365D)),
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
