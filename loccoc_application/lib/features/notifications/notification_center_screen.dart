import 'dart:async';

import 'package:flutter/material.dart';

import 'notification_controller.dart';
import 'notification_models.dart';
import 'notification_permission.dart';

export 'notification_models.dart'
    show NotificationTarget, NotificationTargetKind;

typedef OpenNotificationTarget = Future<void> Function(
  NotificationTarget target,
);

class NotificationCenterScreen extends StatefulWidget {
  final NotificationController controller;
  final OpenNotificationTarget onOpen;
  final NotificationPermissionAdapter? permissionAdapter;
  const NotificationCenterScreen({
    super.key,
    required this.controller,
    required this.onOpen,
    this.permissionAdapter,
  });
  @override
  State<NotificationCenterScreen> createState() =>
      _NotificationCenterScreenState();
}

class _NotificationCenterScreenState extends State<NotificationCenterScreen> {
  bool _permissionBusy = false;
  String? _permissionResult;
  final Set<int> _failedReads = {};
  @override
  void initState() {
    super.initState();
    _initializeController();
  }

  void _initializeController() {
    final controller = widget.controller;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && widget.controller == controller) {
        unawaited(controller.initialize());
      }
    });
  }

  @override
  void didUpdateWidget(covariant NotificationCenterScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      _failedReads.clear();
      _permissionResult = null;
      _initializeController();
    }
  }

  Future<void> _read(NotificationItem item) async {
    final controller = widget.controller;
    final success = await controller.markRead(item);
    if (!mounted || controller != widget.controller) return;
    setState(() {
      if (success) {
        _failedReads.remove(item.id);
      } else {
        _failedReads.add(item.id);
      }
    });
  }

  Future<void> _open(NotificationItem item) async {
    unawaited(_read(item));
    await widget.onOpen(item.target);
  }

  Future<void> _retry() async {
    final controller = widget.controller;
    if (_failedReads.isNotEmpty) {
      for (final item
          in controller.items
              .where((e) => _failedReads.contains(e.id))
              .toList()) {
        await _read(item);
        if (!mounted || controller != widget.controller) return;
      }
    } else {
      await controller.refresh();
    }
  }

  Future<void> _explainPermission() async {
    final controller = widget.controller;
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Cho phép thông báo?'),
        content: const Text(
          'Thông báo đẩy giúp bạn biết khi có tin nhắn và cập nhật mới. Bạn vẫn dùng được hộp thư nếu từ chối. Chế độ demo chỉ mô phỏng, không xin quyền hệ điều hành.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Để sau'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Tiếp tục'),
          ),
        ],
      ),
    );
    if (accepted != true || !mounted || controller != widget.controller) return;
    setState(() => _permissionBusy = true);
    try {
      final result =
          await (widget.permissionAdapter ??
                  DemoNotificationPermissionAdapter())
              .request();
      if (!mounted || controller != widget.controller) return;
      setState(
        () => _permissionResult = switch (result) {
          NotificationPermissionResult.simulated =>
            'Đã mô phỏng lựa chọn. Chưa cấp quyền OS; không có push thật.',
          NotificationPermissionResult.granted =>
            'Đã cho phép thông báo trên thiết bị.',
          NotificationPermissionResult.denied =>
            'Bạn đã từ chối thông báo. Hộp thư vẫn hoạt động.',
          NotificationPermissionResult.unavailable =>
            'Thông báo đẩy chưa khả dụng trên thiết bị này.',
        },
      );
    } catch (_) {
      if (mounted && controller == widget.controller) {
        setState(
          () =>
              _permissionResult = 'Không thể xin quyền. Hộp thư vẫn hoạt động.',
        );
      }
    } finally {
      if (mounted && controller == widget.controller) {
        setState(() => _permissionBusy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: widget.controller,
    builder: (context, _) {
      final c = widget.controller;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            child: Wrap(
              spacing: 12,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Text(
                  'Thông báo',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                Text(
                  '${c.unreadCount} chưa đọc',
                  key: const Key('notification-unread-count'),
                ),
                TextButton(
                  onPressed: c.markingAllRead
                      ? null
                      : () async {
                          await c.markAllRead();
                        },
                  child: const Text('Đọc tất cả'),
                ),
                IconButton(
                  tooltip: 'Tải lại thông báo',
                  onPressed: c.loading ? null : c.refresh,
                  icon: const Icon(Icons.refresh),
                ),
              ],
            ),
          ),
          if (c.isDemo)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Wrap(
                spacing: 8,
                children: [
                  const Chip(label: Text('DEMO • dữ liệu mô phỏng')),
                  TextButton.icon(
                    onPressed: c.simulateForegroundEvent,
                    icon: const Icon(Icons.add_alert_outlined),
                    label: const Text('Tạo thông báo demo'),
                  ),
                  TextButton(
                    onPressed: () => c.setDemoOnline(!c.demoOnline),
                    child: Text(
                      c.demoOnline ? 'Mô phỏng mất mạng' : 'Kết nối lại demo',
                    ),
                  ),
                ],
              ),
            ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Align(
              alignment: Alignment.centerLeft,
              child: TextButton(
                onPressed: _permissionBusy ? null : _explainPermission,
                child: const Text('Cài đặt thông báo'),
              ),
            ),
          ),
          if (_permissionResult != null)
            Padding(
              padding: const EdgeInsets.all(12),
              child: Text(_permissionResult!),
            ),
          if (c.error != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      c.error!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ),
                  TextButton(onPressed: _retry, child: const Text('Thử lại')),
                ],
              ),
            ),
          if (c.loading) const LinearProgressIndicator(),
          Expanded(
            child: RefreshIndicator(
              onRefresh: c.refresh,
              child: NotificationListener<ScrollNotification>(
                onNotification: (notification) {
                  if (notification.metrics.extentAfter < 160) {
                    unawaited(c.loadMore());
                  }
                  return false;
                },
                child: ListView.builder(
                  key: const Key('notification-inbox'),
                  physics: const AlwaysScrollableScrollPhysics(),
                  itemCount: c.items.isEmpty ? 1 : c.items.length + 1,
                  itemBuilder: (context, index) {
                    if (c.items.isEmpty) {
                      return Padding(
                        padding: const EdgeInsets.all(24),
                        child: Text(
                          c.loading
                              ? 'Đang tải thông báo…'
                              : 'Chưa có thông báo.',
                        ),
                      );
                    }
                    if (index == c.items.length) {
                      return c.loadingMore
                          ? const Center(
                              child: Padding(
                                padding: EdgeInsets.all(16),
                                child: CircularProgressIndicator(),
                              ),
                            )
                          : c.hasMore
                          ? Center(
                              child: TextButton(
                                onPressed: c.loadMore,
                                child: const Text('Tải thêm'),
                              ),
                            )
                          : const SizedBox(height: 24);
                    }
                    final item = c.items[index];
                    return ListTile(
                      key: ValueKey('notification-${item.id}'),
                      leading: Icon(
                        item.read
                            ? Icons.notifications_none
                            : Icons.notifications_active,
                        color: item.read
                            ? null
                            : Theme.of(context).colorScheme.primary,
                      ),
                      title: Text(
                        item.title,
                        style: TextStyle(
                          fontWeight: item.read
                              ? FontWeight.normal
                              : FontWeight.bold,
                        ),
                      ),
                      subtitle: Text(
                        '${item.body}${item.target.isAvailable ? '' : '\nNội dung không còn khả dụng'}',
                      ),
                      isThreeLine: !item.target.isAvailable,
                      onTap: () => _open(item),
                      trailing: !item.read
                          ? const Icon(
                              Icons.circle,
                              size: 8,
                              semanticLabel: 'Chưa đọc',
                            )
                          : null,
                    );
                  },
                ),
              ),
            ),
          ),
        ],
      );
    },
  );
}

/// Shell can place this above all tabs. Receiving a banner never marks it read.
class NotificationForegroundBanner extends StatelessWidget {
  final NotificationController controller;
  final OpenNotificationTarget onOpen;
  const NotificationForegroundBanner({
    super.key,
    required this.controller,
    required this.onOpen,
  });
  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: controller,
    builder: (context, _) {
      final item = controller.foregroundNotification;
      if (item == null) return const SizedBox.shrink();
      return MaterialBanner(
        content: Text('${item.title}\n${item.body}'),
        leading: const Icon(Icons.notifications_active),
        actions: [
          TextButton(
            onPressed: controller.dismissBanner,
            child: const Text('Đóng'),
          ),
          TextButton(
            onPressed: () async {
              controller.dismissBanner();
              unawaited(controller.markRead(item));
              await onOpen(item.target);
            },
            child: const Text('Mở'),
          ),
        ],
      );
    },
  );
}
