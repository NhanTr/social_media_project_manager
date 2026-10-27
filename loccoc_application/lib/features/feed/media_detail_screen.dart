import 'dart:async';

import 'package:flutter/material.dart';

import '../../core/network/posts_api.dart';
import 'media_detail_controller.dart';

class MediaDetailScreen extends StatefulWidget {
  const MediaDetailScreen({
    super.key,
    required this.api,
    required this.postId,
    required this.onUnavailable,
    this.controller,
    this.isSessionActive,
    this.accessChanges,
  });
  final PostsApi api;
  final int postId;
  final FutureOr<void> Function(int) onUnavailable;
  final MediaDetailController? controller;
  final bool Function()? isSessionActive;
  final Listenable? accessChanges;
  @override
  State<MediaDetailScreen> createState() => _MediaDetailScreenState();
}

class _MediaDetailScreenState extends State<MediaDetailScreen> {
  late final MediaDetailController _controller;
  @override
  void initState() {
    super.initState();
    _controller =
        widget.controller ??
        MediaDetailController(
          api: widget.api,
          postId: widget.postId,
          onUnavailable: widget.onUnavailable,
          isSessionActive: widget.isSessionActive,
        );
    widget.accessChanges?.addListener(_checkAccess);
    unawaited(_controller.load());
  }

  void _checkAccess() {
    if (widget.isSessionActive?.call() == false) {
      unawaited(_controller.invalidateAccess());
    } else if (!_controller.loading &&
        !_controller.saving &&
        !_controller.unavailable) {
      // A partial feed page cannot prove that an older open post still exists.
      unawaited(_controller.load());
    }
  }

  @override
  void dispose() {
    widget.accessChanges?.removeListener(_checkAccess);
    if (widget.controller == null) _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _controller,
    builder: (context, _) {
      final c = _controller;
      final post = c.post;
      return Scaffold(
        appBar: AppBar(
          title: const Text('Chi tiết ảnh'),
          actions: [
            IconButton(
              tooltip: 'Lưu vào thư viện',
              onPressed: c.loading || c.saving || c.unavailable || post == null
                  ? null
                  : c.save,
              icon: c.saving
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.download_outlined),
            ),
          ],
        ),
        body: c.loading
            ? const Center(child: CircularProgressIndicator())
            : Column(
                children: [
                  if (c.message != null)
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Text(
                        c.message!,
                        key: const Key('detail-message'),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  if (post != null) ...[
                    Expanded(
                      child: InteractiveViewer(
                        minScale: 0.5,
                        maxScale: 5,
                        child: Center(
                          child: Image.network(
                            post.mediaUrl,
                            fit: BoxFit.contain,
                            errorBuilder: (_, _, _) => const Padding(
                              padding: EdgeInsets.all(24),
                              child: Text(
                                'Không thể tải ảnh. Kiểm tra kết nối và thử lại.',
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        children: [
                          Text(
                            post.authorName.isEmpty
                                ? 'Bạn bè'
                                : post.authorName,
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          if (post.caption?.isNotEmpty == true)
                            Text(post.caption!),
                          const SizedBox(height: 8),
                          const Text(
                            'Chụm hoặc kéo hai ngón tay để phóng to ảnh.',
                          ),
                        ],
                      ),
                    ),
                    if (!c.gallery.supported)
                      const Padding(
                        padding: EdgeInsets.all(12),
                        child: Text(
                          'Lưu ảnh chỉ được hỗ trợ trên Android hoặc iOS.',
                        ),
                      ),
                  ] else if (!c.unavailable)
                    Center(
                      child: FilledButton.tonal(
                        onPressed: c.load,
                        child: const Text('Thử lại'),
                      ),
                    ),
                ],
              ),
      );
    },
  );
}
