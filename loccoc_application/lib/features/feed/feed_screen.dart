import 'dart:async';

import 'package:flutter/material.dart';

import '../../core/models/post_models.dart';
import '../../core/models/user_models.dart';
import '../../core/network/posts_api.dart';
import 'feed_controller.dart';
import 'media_detail_screen.dart';

class FeedScreen extends StatefulWidget {
  const FeedScreen({
    super.key,
    required this.api,
    required this.accountId,
    required this.onCompose,
    this.controller,
  });
  final PostsApi api;
  final int accountId;
  final VoidCallback onCompose;

  /// Injected controllers remain owned by the caller.
  final FeedController? controller;
  @override
  State<FeedScreen> createState() => FeedScreenState();
}

class FeedScreenState extends State<FeedScreen> {
  late FeedController _controller;
  final ScrollController _scroll = ScrollController();
  List<FriendSummary> _friends = [];
  bool _friendsFailed = false;
  bool _loadingFriends = false;
  int _friendsGeneration = 0;

  @override
  void initState() {
    super.initState();
    _installController();
    _scroll.addListener(_onScroll);
  }

  void _installController() {
    _controller =
        widget.controller ??
        FeedController(api: widget.api, accountId: widget.accountId);
    _friends = [];
    unawaited(_loadFriends());
    unawaited(_controller.refresh());
  }

  @override
  void didUpdateWidget(covariant FeedScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.api != widget.api ||
        oldWidget.accountId != widget.accountId ||
        oldWidget.controller != widget.controller) {
      ++_friendsGeneration;
      if (oldWidget.controller == null) _controller.dispose();
      _installController();
    }
  }

  Future<void> _loadFriends() async {
    final generation = ++_friendsGeneration;
    _loadingFriends = true;
    _friendsFailed = false;
    try {
      final result = await widget.api.friends();
      if (!mounted || generation != _friendsGeneration) return;
      setState(() {
        _friends = result;
        _loadingFriends = false;
      });
    } catch (_) {
      if (!mounted || generation != _friendsGeneration) return;
      setState(() {
        _friendsFailed = true;
        _loadingFriends = false;
      });
    }
  }

  Future<void> refresh() {
    unawaited(_loadFriends());
    return _controller.refresh();
  }

  Future<void> invalidateFriendships(Set<int> currentFriendIds) async {
    _friends = _friends.where((f) => currentFriendIds.contains(f.id)).toList();
    if (mounted) setState(() {});
    final invalidated = _controller.invalidateFriendships(currentFriendIds);
    unawaited(_loadFriends());
    await invalidated;
  }

  Future<void> invalidateSession() => _controller.invalidateSession();
  void _onScroll() {
    if (_scroll.hasClients && _scroll.position.extentAfter < 400) {
      unawaited(_controller.loadMore());
    }
  }

  @override
  void dispose() {
    ++_friendsGeneration;
    _scroll.dispose();
    if (widget.controller == null) _controller.dispose();
    super.dispose();
  }

  void _openDetail(PostModel post) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => MediaDetailScreen(
          api: widget.api,
          postId: post.id,
          onUnavailable: _controller.removePost,
          isSessionActive: () => _controller.canViewPost(post),
          accessChanges: _controller,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _controller,
    builder: (context, _) {
      final c = _controller;
      final selected = _friends.any((f) => f.id == c.friendId)
          ? c.friendId
          : null;
      return Scaffold(
        appBar: AppBar(
          title: const Text('Bảng tin'),
          actions: [
            IconButton(
              tooltip: 'Làm mới',
              onPressed: c.loading ? null : refresh,
              icon: const Icon(Icons.refresh),
            ),
            IconButton(
              tooltip: 'Chia sẻ ảnh',
              onPressed: widget.onCompose,
              icon: const Icon(Icons.add_a_photo_outlined),
            ),
          ],
        ),
        body: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: Row(
                children: [
                  const Icon(Icons.people_outline),
                  const SizedBox(width: 12),
                  Expanded(
                    child: DropdownButton<int?>(
                      isExpanded: true,
                      value: selected,
                      hint: const Text('Tất cả bạn bè'),
                      items: [
                        const DropdownMenuItem<int?>(
                          value: null,
                          child: Text('Tất cả bạn bè'),
                        ),
                        for (final friend in _friends)
                          DropdownMenuItem<int?>(
                            value: friend.id,
                            child: Text(
                              friend.displayName,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                      ],
                      onChanged: (id) => unawaited(c.setFriend(id)),
                    ),
                  ),
                  if (_loadingFriends)
                    const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                  if (_friendsFailed)
                    IconButton(
                      tooltip: 'Tải lại danh sách bạn bè',
                      onPressed: _loadFriends,
                      icon: const Icon(Icons.sync_problem),
                    ),
                ],
              ),
            ),
            if (c.offline)
              Container(
                width: double.infinity,
                color: Theme.of(context).colorScheme.secondaryContainer,
                padding: const EdgeInsets.all(12),
                child: Text(
                  c.lastSuccess == null
                      ? 'Ngoại tuyến · Chưa có bảng tin đã lưu trên thiết bị này.'
                      : 'Ngoại tuyến · Đang hiển thị bản đã lưu lúc ${_time(c.lastSuccess!)}.',
                ),
              ),
            if (c.error != null && !c.offline)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  children: [
                    Expanded(child: Text(c.error!)),
                    TextButton(
                      onPressed: c.loading ? null : refresh,
                      child: const Text('Thử lại'),
                    ),
                  ],
                ),
              ),
            Expanded(
              child: RefreshIndicator(
                onRefresh: refresh,
                child: CustomScrollView(
                  key: PageStorageKey('feed-${widget.accountId}-${c.friendId}'),
                  controller: _scroll,
                  physics: const AlwaysScrollableScrollPhysics(),
                  slivers: [
                    if (c.loading && c.items.isEmpty)
                      SliverToBoxAdapter(child: _skeleton(context))
                    else if (c.items.isEmpty)
                      SliverFillRemaining(
                        hasScrollBody: false,
                        child: Center(
                          child: Padding(
                            padding: const EdgeInsets.all(24),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  c.forbidden
                                      ? Icons.lock_outline
                                      : c.offline
                                      ? Icons.cloud_off
                                      : Icons.photo_library_outlined,
                                  size: 64,
                                  color: Theme.of(context).colorScheme.outline,
                                ),
                                const SizedBox(height: 16),
                                Text(
                                  c.forbidden
                                      ? 'Nội dung không khả dụng'
                                      : c.offline
                                      ? 'Không thể kết nối bảng tin'
                                      : 'Chưa có ảnh từ bạn bè',
                                  style: Theme.of(context).textTheme.titleLarge,
                                  textAlign: TextAlign.center,
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  c.friendId == null
                                      ? 'Kết nối với bạn bè để xem những khoảnh khắc mới.'
                                      : 'Người bạn này chưa có ảnh để hiển thị.',
                                  textAlign: TextAlign.center,
                                ),
                                const SizedBox(height: 16),
                                FilledButton.icon(
                                  onPressed: widget.onCompose,
                                  icon: const Icon(Icons.add_a_photo_outlined),
                                  label: const Text('Chia sẻ ảnh của bạn'),
                                ),
                              ],
                            ),
                          ),
                        ),
                      )
                    else
                      SliverList.builder(
                        itemCount: c.items.length,
                        itemBuilder: (context, index) =>
                            _postCard(context, c.items[index]),
                      ),
                    if (c.items.isNotEmpty)
                      SliverToBoxAdapter(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Center(
                            child: c.loadingMore
                                ? const CircularProgressIndicator()
                                : c.hasNext
                                ? TextButton(
                                    onPressed: c.loadMore,
                                    child: const Text('Tải thêm'),
                                  )
                                : const Text('Bạn đã xem hết bảng tin.'),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ],
        ),
      );
    },
  );
  Widget _skeleton(BuildContext context) => Column(
    key: const Key('feed-skeleton'),
    children: [
      for (var i = 0; i < 2; i++)
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                height: 18,
                width: 140,
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
              ),
              const SizedBox(height: 12),
              Container(
                height: 220,
                width: double.infinity,
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
              ),
            ],
          ),
        ),
    ],
  );
  Widget _postCard(BuildContext context, PostModel post) {
    final bytes = _controller.previewFor(post.id);
    Widget imageError(BuildContext _, Object error, StackTrace? stack) =>
        const SizedBox(
          height: 220,
          child: Center(child: Icon(Icons.broken_image_outlined, size: 48)),
        );
    final image = bytes != null
        ? Image.memory(
            bytes,
            fit: BoxFit.cover,
            width: double.infinity,
            height: 260,
            cacheWidth: 1024,
            errorBuilder: imageError,
          )
        : _controller.offline
        ? const SizedBox(
            height: 220,
            child: Center(child: Text('Ảnh chưa được lưu ngoại tuyến.')),
          )
        : Image.network(
            post.thumbnailUrl ?? post.mediaUrl,
            fit: BoxFit.cover,
            width: double.infinity,
            height: 260,
            cacheWidth: 1024,
            errorBuilder: imageError,
          );
    return Card(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 12),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => _openDetail(post),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ListTile(
              leading: const CircleAvatar(child: Icon(Icons.person_outline)),
              title: Text(post.authorName.isEmpty ? 'Bạn bè' : post.authorName),
              subtitle: Text(_time(post.createdAt)),
            ),
            Semantics(
              label: 'Mở ảnh của ${post.authorName}',
              button: true,
              child: image,
            ),
            if (post.caption?.isNotEmpty == true)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(post.caption!),
              ),
          ],
        ),
      ),
    );
  }

  String _time(DateTime time) {
    final local = time.toLocal();
    return '${local.day}/${local.month}/${local.year} ${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  }
}
