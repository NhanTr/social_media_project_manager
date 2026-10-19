import 'dart:async';

import 'package:flutter/material.dart';

import '../friends_controller.dart';

class FriendsScreen extends StatefulWidget {
  final FriendsController controller;

  const FriendsScreen({super.key, required this.controller});

  @override
  State<FriendsScreen> createState() => _FriendsScreenState();
}

class _FriendsScreenState extends State<FriendsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final TextEditingController _searchController = TextEditingController();
  Timer? _debounceTimer;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    widget.controller.loadAll();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _searchController.dispose();
    _debounceTimer?.cancel();
    super.dispose();
  }

  void _onSearchChanged(String query) {
    _debounceTimer?.cancel();
    _debounceTimer = Timer(const Duration(milliseconds: 350), () {
      widget.controller.searchUsers(query);
    });
  }

  void _clearSearch() {
    _searchController.clear();
    widget.controller.clearSearch();
  }

  Future<void> _confirmBlock(int userId, String name) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Chặn $name?'),
        content: const Text(
          'Người dùng bị chặn sẽ không thể xem bài viết của bạn, không thể gửi tin nhắn hoặc kết bạn.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Chặn người dùng'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      final ok = await widget.controller.blockUser(userId);
      if (ok && mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Đã chặn $name thành công')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) {
        final ctrl = widget.controller;

        return Scaffold(
          appBar: AppBar(
            title: const Text(
              'Bạn bè & Kết nối',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            backgroundColor: const Color(0xFF17365D),
            foregroundColor: Colors.white,
            bottom: PreferredSize(
              preferredSize: const Size.fromHeight(104),
              child: Column(
                children: [
                  // Search Bar
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 8,
                    ),
                    child: Container(
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: TextField(
                        controller: _searchController,
                        onChanged: _onSearchChanged,
                        decoration: InputDecoration(
                          hintText: 'Tìm kiếm username hoặc tên...',
                          prefixIcon: const Icon(
                            Icons.search,
                            color: Color(0xFF17365D),
                          ),
                          suffixIcon: _searchController.text.isNotEmpty
                              ? IconButton(
                                  icon: const Icon(Icons.clear),
                                  onPressed: _clearSearch,
                                )
                              : null,
                          border: InputBorder.none,
                          contentPadding: const EdgeInsets.symmetric(
                            vertical: 12,
                          ),
                        ),
                      ),
                    ),
                  ),

                  // Tab Bar (hidden when actively displaying search results)
                  if (_searchController.text.trim().isEmpty)
                    TabBar(
                      controller: _tabController,
                      indicatorColor: Colors.white,
                      indicatorWeight: 3,
                      labelColor: Colors.white,
                      unselectedLabelColor: Colors.white70,
                      tabs: [
                        Tab(text: 'Bạn bè (${ctrl.friends.length})'),
                        Tab(text: 'Lời mời (${ctrl.incomingRequests.length})'),
                        Tab(text: 'Đã chặn (${ctrl.blockedUsers.length})'),
                      ],
                    ),
                ],
              ),
            ),
          ),
          bottomNavigationBar: ctrl.errorMessage == null
              ? null
              : MaterialBanner(
                  content: Text(ctrl.errorMessage!),
                  actions: [
                    TextButton(
                      onPressed: () async {
                        await ctrl.loadAll();
                        if (_searchController.text.trim().isNotEmpty) {
                          await ctrl.searchUsers(_searchController.text);
                        }
                      },
                      child: const Text('Thử lại'),
                    ),
                  ],
                ),
          body: ctrl.isLoading
              ? const Center(child: CircularProgressIndicator())
              : _searchController.text.trim().isNotEmpty
              ? _buildSearchResults(ctrl)
              : TabBarView(
                  controller: _tabController,
                  children: [
                    _buildFriendsTab(ctrl),
                    _buildRequestsTab(ctrl),
                    _buildBlockedTab(ctrl),
                  ],
                ),
        );
      },
    );
  }

  Widget _buildSearchResults(FriendsController ctrl) {
    if (ctrl.isSearching) {
      return const Center(child: CircularProgressIndicator());
    }

    if (ctrl.searchResults.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.person_search_outlined,
              size: 64,
              color: Colors.grey.shade400,
            ),
            const SizedBox(height: 12),
            Text(
              'Không tìm thấy người dùng khớp với "${_searchController.text}"',
              style: TextStyle(color: Colors.grey.shade600, fontSize: 14),
            ),
          ],
        ),
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: ctrl.searchResults.length,
      separatorBuilder: (ctx, i) => const Divider(),
      itemBuilder: (context, index) {
        final item = ctrl.searchResults[index];
        final user = item.user;

        return ListTile(
          leading: CircleAvatar(
            backgroundColor: const Color(0xFF17365D),
            foregroundColor: Colors.white,
            child: Text(
              user.displayName.isNotEmpty
                  ? user.displayName[0].toUpperCase()
                  : 'U',
            ),
          ),
          title: Text(
            user.displayName,
            style: const TextStyle(fontWeight: FontWeight.bold),
          ),
          subtitle: Text('@${user.username}'),
          trailing: item.isBlocked
              ? const Chip(
                  label: Text(
                    'Đã chặn',
                    style: TextStyle(fontSize: 12, color: Colors.red),
                  ),
                )
              : item.areFriends
              ? const Chip(
                  label: Text(
                    'Bạn bè',
                    style: TextStyle(fontSize: 12, color: Colors.green),
                  ),
                )
              : item.pendingRequestId != null
              ? const Chip(
                  label: Text(
                    'Đang chờ',
                    style: TextStyle(fontSize: 12, color: Colors.orange),
                  ),
                )
              : Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    FilledButton.tonal(
                      onPressed: () async {
                        final messenger = ScaffoldMessenger.of(this.context);
                        final ok = await ctrl.sendFriendRequest(user.id);
                        if (ok && mounted) {
                          messenger.showSnackBar(
                            SnackBar(
                              content: Text(
                                'Đã gửi lời mời đến ${user.displayName}',
                              ),
                            ),
                          );
                          _onSearchChanged(_searchController.text);
                        }
                      },
                      child: const Text('Kết bạn'),
                    ),
                    IconButton(
                      icon: const Icon(
                        Icons.block,
                        size: 20,
                        color: Colors.grey,
                      ),
                      tooltip: 'Chặn',
                      onPressed: () => _confirmBlock(user.id, user.displayName),
                    ),
                  ],
                ),
        );
      },
    );
  }

  Widget _buildFriendsTab(FriendsController ctrl) {
    if (ctrl.friends.isEmpty) {
      return RefreshIndicator(
        onRefresh: ctrl.loadAll,
        child: ListView(
          children: [
            SizedBox(
              height: MediaQuery.of(context).size.height * 0.5,
              child: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.people_outline,
                      size: 72,
                      color: Colors.grey.shade400,
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'Bạn chưa có người bạn nào',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: Colors.black87,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Tìm kiếm username ở trên để gửi lời mời kết bạn!',
                      style: TextStyle(
                        color: Colors.grey.shade600,
                        fontSize: 13,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: ctrl.loadAll,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(vertical: 8),
        itemCount: ctrl.friends.length,
        separatorBuilder: (ctx, i) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final friend = ctrl.friends[index];
          return ListTile(
            leading: CircleAvatar(
              backgroundColor: const Color(0xFF17365D),
              foregroundColor: Colors.white,
              child: Text(
                friend.displayName.isNotEmpty
                    ? friend.displayName[0].toUpperCase()
                    : 'U',
              ),
            ),
            title: Text(
              friend.displayName,
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
            subtitle: Text(
              '@${friend.username} ${friend.bio != null ? "• ${friend.bio}" : ""}',
            ),
            trailing: PopupMenuButton<String>(
              onSelected: (val) {
                if (val == 'block') {
                  _confirmBlock(friend.id, friend.displayName);
                }
              },
              itemBuilder: (context) => [
                const PopupMenuItem(
                  value: 'block',
                  child: Row(
                    children: [
                      Icon(Icons.block, color: Colors.red, size: 20),
                      SizedBox(width: 8),
                      Text(
                        'Chặn người này',
                        style: TextStyle(color: Colors.red),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildRequestsTab(FriendsController ctrl) {
    if (ctrl.incomingRequests.isEmpty && ctrl.outgoingRequests.isEmpty) {
      return RefreshIndicator(
        onRefresh: ctrl.loadAll,
        child: ListView(
          children: [
            SizedBox(
              height: MediaQuery.of(context).size.height * 0.5,
              child: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.mail_outline,
                      size: 72,
                      color: Colors.grey.shade400,
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'Không có lời mời kết bạn nào',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Lời mời kết bạn gửi đến hoặc đã gửi sẽ xuất hiện ở đây.',
                      style: TextStyle(
                        color: Colors.grey.shade600,
                        fontSize: 13,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: ctrl.loadAll,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (ctrl.incomingRequests.isNotEmpty) ...[
            const Text(
              'Lời mời đã nhận',
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
                color: Color(0xFF17365D),
              ),
            ),
            const SizedBox(height: 8),
            ...ctrl.incomingRequests.map((req) {
              return Card(
                elevation: 1,
                margin: const EdgeInsets.only(bottom: 8),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(12.0),
                  child: Row(
                    children: [
                      CircleAvatar(
                        backgroundColor: const Color(0xFF17365D),
                        foregroundColor: Colors.white,
                        child: Text('${req.senderId}'),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Người dùng #${req.senderId}',
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              'Đã gửi lời mời kết bạn',
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.grey.shade600,
                              ),
                            ),
                          ],
                        ),
                      ),
                      FilledButton(
                        onPressed: ctrl.isPending('request:${req.id}')
                            ? null
                            : () => ctrl.acceptFriendRequest(req.id),
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFF17365D),
                        ),
                        child: const Text('Đồng ý'),
                      ),
                      const SizedBox(width: 6),
                      OutlinedButton(
                        onPressed: ctrl.isPending('request:${req.id}')
                            ? null
                            : () => ctrl.declineFriendRequest(req.id),
                        child: const Text('Từ chối'),
                      ),
                    ],
                  ),
                ),
              );
            }),
            const SizedBox(height: 20),
          ],

          if (ctrl.outgoingRequests.isNotEmpty) ...[
            const Text(
              'Lời mời đã gửi',
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
                color: Color(0xFF17365D),
              ),
            ),
            const SizedBox(height: 8),
            ...ctrl.outgoingRequests.map((req) {
              return Card(
                elevation: 0,
                color: Colors.grey.shade100,
                margin: const EdgeInsets.only(bottom: 8),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                child: ListTile(
                  leading: const Icon(
                    Icons.send_rounded,
                    color: Colors.blueGrey,
                  ),
                  title: Text(
                    'Đang chờ người dùng #${req.receiverId} phản hồi',
                  ),
                  subtitle: Text('Trạng thái: ${req.status}'),
                  trailing: TextButton(
                    onPressed: ctrl.isPending('request:${req.id}')
                        ? null
                        : () => ctrl.cancelFriendRequest(req.id),
                    child: const Text('Hủy lời mời'),
                  ),
                ),
              );
            }),
          ],
        ],
      ),
    );
  }

  Widget _buildBlockedTab(FriendsController ctrl) {
    if (ctrl.blockedUsers.isEmpty) {
      return RefreshIndicator(
        onRefresh: ctrl.loadAll,
        child: ListView(
          children: [
            SizedBox(
              height: MediaQuery.of(context).size.height * 0.5,
              child: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.shield_outlined,
                      size: 72,
                      color: Colors.grey.shade400,
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'Danh sách chặn trống',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Bạn chưa chặn người dùng nào.',
                      style: TextStyle(
                        color: Colors.grey.shade600,
                        fontSize: 13,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: ctrl.loadAll,
      child: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: ctrl.blockedUsers.length,
        separatorBuilder: (ctx, i) => const Divider(),
        itemBuilder: (context, index) {
          final block = ctrl.blockedUsers[index];
          return ListTile(
            leading: const CircleAvatar(
              backgroundColor: Colors.red,
              foregroundColor: Colors.white,
              child: Icon(Icons.block, size: 20),
            ),
            title: Text(
              'Người dùng bị chặn #${block.blockedId}',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            trailing: OutlinedButton(
              onPressed: () async {
                final messenger = ScaffoldMessenger.of(this.context);
                final ok = await ctrl.unblockUser(block.blockedId);
                if (ok && mounted) {
                  messenger.showSnackBar(
                    const SnackBar(content: Text('Đã bỏ chặn thành công')),
                  );
                }
              },
              child: const Text('Bỏ chặn'),
            ),
          );
        },
      ),
    );
  }
}
