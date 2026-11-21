import 'dart:async';

import 'package:flutter/material.dart';

import 'chat_controller.dart';

final chatRouteObserver = RouteObserver<ModalRoute<dynamic>>();

class ConversationListScreen extends StatelessWidget {
  final ChatController controller;
  final bool active;
  const ConversationListScreen({
    super.key,
    required this.controller,
    required this.active,
  });
  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: controller,
    builder: (context, _) => Scaffold(
      appBar: AppBar(title: const Text('Trò chuyện')),
      body: Column(
        children: [
          _ConnectionBar(controller: controller),
          if (controller.error != null) _ErrorBanner(controller.error!),
          Expanded(
            child: controller.conversations.isEmpty
                ? const Center(child: Text('Chưa có cuộc trò chuyện nào'))
                : RefreshIndicator(
                    onRefresh: controller.reconnect,
                    child: ListView.builder(
                      itemCount: controller.conversations.length,
                      itemBuilder: (context, index) {
                        final conversation = controller.conversations[index];
                        final messages = controller.messagesFor(
                          conversation.id,
                        );
                        return ListTile(
                          leading: const CircleAvatar(
                            child: Icon(Icons.person_outline),
                          ),
                          title: Text(conversation.title),
                          subtitle: Text(
                            messages.isEmpty
                                ? 'Bắt đầu trò chuyện'
                                : messages.last.content,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: !active
                              ? null
                              : () => Navigator.of(context).push(
                                  MaterialPageRoute<void>(
                                    builder: (_) => ChatRoomScreen(
                                      controller: controller,
                                      conversationId: conversation.id,
                                    ),
                                  ),
                                ),
                        );
                      },
                    ),
                  ),
          ),
        ],
      ),
    ),
  );
}

class ChatRoomScreen extends StatefulWidget {
  final ChatController controller;
  final int conversationId;
  final int? focusMessageId;
  const ChatRoomScreen({
    super.key,
    required this.controller,
    required this.conversationId,
    this.focusMessageId,
  });
  @override
  State<ChatRoomScreen> createState() => _ChatRoomScreenState();
}

class _ChatRoomScreenState extends State<ChatRoomScreen>
    with RouteAware, WidgetsBindingObserver {
  final _text = TextEditingController();
  final _scroll = ScrollController();
  final _viewport = GlobalKey();
  final Map<int, GlobalKey> _rows = {};
  ModalRoute<dynamic>? _route;
  bool _unavailable = false,
      _current = false,
      _scheduled = false,
      _sending = false;
  int? _focused;
  bool _opening = true;
  int _focusSteps = 0;
  ChatController get controller => widget.controller;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    controller.addListener(_changed);
    _scroll.addListener(_onScroll);
    unawaited(_open());
  }

  Future<void> _open() async {
    try {
      await controller.openConversation(widget.conversationId);
      if (!mounted) return;
      final focus = widget.focusMessageId;
      if (focus != null) {
        // Load bounded cursor pages until the target is found, or show a safe
        // unavailable note. No guessed conversation from a message ID.
        while (mounted &&
            !controller
                .messagesFor(widget.conversationId)
                .any((m) => m.id == focus) &&
            controller.hasMore(widget.conversationId)) {
          final before = controller.messagesFor(widget.conversationId).length;
          await controller.loadOlder(widget.conversationId);
          if (controller.messagesFor(widget.conversationId).length == before) {
            break;
          }
        }
        if (!mounted) return;
        if (!controller
            .messagesFor(widget.conversationId)
            .any((m) => m.id == focus)) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Tin nhắn không khả dụng')),
          );
        } else {
          _focused = focus;
        }
      }
      _opening = false;
      _changed();
    } catch (_) {
      if (mounted) {
        setState(() {
          _opening = false;
          _unavailable = true;
        });
      }
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final route = ModalRoute.of(context);
    if (_route != route) {
      if (_route != null) chatRouteObserver.unsubscribe(this);
      _route = route;
      if (route != null) chatRouteObserver.subscribe(this, route);
    }
    _setCurrent(route?.isCurrent ?? false);
  }

  void _setCurrent(bool current) {
    _current = current;
    controller.setRoomVisible(current ? widget.conversationId : null);
    if (current) _scheduleVisible();
  }

  @override
  void didPush() => _setCurrent(true);
  @override
  void didPopNext() => _setCurrent(true);
  @override
  void didPushNext() => _setCurrent(false);
  @override
  void didPop() => _setCurrent(false);
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    controller.setForeground(state == AppLifecycleState.resumed);
    if (state == AppLifecycleState.resumed) _scheduleVisible();
  }

  void _changed() {
    if (mounted) {
      setState(() {});
      _scheduleVisible();
    }
  }

  void _onScroll() {
    _scheduleVisible();
    if (_scroll.hasClients &&
        _scroll.position.extentAfter < 160 &&
        controller.hasMore(widget.conversationId)) {
      unawaited(controller.loadOlder(widget.conversationId));
    }
  }

  void _scheduleVisible() {
    if (_scheduled || !mounted) return;
    _scheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scheduled = false;
      if (!mounted || !_current || !(_route?.isCurrent ?? false)) return;
      // Keep only mounted row keys so long-history pagination stays lazy.
      _rows.removeWhere((_, key) => key.currentContext == null);
      final viewport = _viewport.currentContext?.findRenderObject();
      if (viewport is! RenderBox || !viewport.hasSize) return;
      if (_opening) return;
      final focus = _focused;
      if (focus != null) {
        final target = _rows[focus]?.currentContext;
        if (target != null) {
          _focused = null;
          unawaited(
            Scrollable.ensureVisible(
              target,
              alignment: 0.5,
            ).then((_) => _scheduleVisible()),
          );
        } else if (_scroll.hasClients && _focusSteps++ < 1000) {
          final position = _scroll.position;
          final next = (position.pixels + viewport.size.height * 0.8).clamp(
            0.0,
            position.maxScrollExtent,
          );
          if (next > position.pixels) {
            _scroll.jumpTo(next);
            _scheduleVisible();
          } else {
            _focused = null;
            _scheduleVisible();
          }
        } else {
          _focused = null;
        }
        return;
      }
      final bounds = viewport.localToGlobal(Offset.zero) & viewport.size;
      final visible = <int>{};
      for (final entry in _rows.entries) {
        final box = entry.value.currentContext?.findRenderObject();
        if (box is! RenderBox || !box.hasSize || !box.attached) continue;
        final rect = box.localToGlobal(Offset.zero) & box.size;
        if (rect.overlaps(bounds) && rect.intersect(bounds).height > 1) {
          visible.add(entry.key);
        }
      }
      unawaited(controller.reportVisible(widget.conversationId, visible));
    });
  }

  Future<void> _send() async {
    if (_sending || _text.text.trim().isEmpty) return;
    final text = _text.text;
    final keys = controller
        .messagesFor(widget.conversationId)
        .map((m) => m.clientMessageId)
        .toSet();
    setState(() => _sending = true);
    await controller.sendText(widget.conversationId, text);
    if (!mounted) return;
    if (_text.text == text &&
        controller
            .messagesFor(widget.conversationId)
            .any(
              (m) =>
                  m.content == text.trim() &&
                  m.senderId == controller.accountId &&
                  !keys.contains(m.clientMessageId),
            )) {
      _text.clear();
    }
    setState(() => _sending = false);
    _scheduleVisible();
  }

  @override
  Widget build(BuildContext context) {
    final matches = controller.conversations.where(
      (c) => c.id == widget.conversationId,
    );
    final messages = controller
        .messagesFor(widget.conversationId)
        .reversed
        .toList();
    return Scaffold(
      appBar: AppBar(
        title: Text(matches.isEmpty ? 'Trò chuyện' : matches.first.title),
      ),
      body: _unavailable
          ? const Center(
              child: Text(
                'Hội thoại không khả dụng hoặc bạn không có quyền truy cập',
              ),
            )
          : Column(
              children: [
                _ConnectionBar(controller: controller),
                if (controller.error != null) _ErrorBanner(controller.error!),
                Expanded(
                  child: NotificationListener<ScrollNotification>(
                    onNotification: (_) {
                      _scheduleVisible();
                      return false;
                    },
                    child: ListView.builder(
                      key: _viewport,
                      controller: _scroll,
                      reverse: true,
                      padding: const EdgeInsets.all(12),
                      itemCount: messages.length + 1,
                      itemBuilder: (context, index) {
                        if (index == messages.length) {
                          return controller.hasMore(widget.conversationId)
                              ? Center(
                                  child: TextButton(
                                    onPressed:
                                        controller.isLoading(
                                          widget.conversationId,
                                        )
                                        ? null
                                        : () => controller.loadOlder(
                                            widget.conversationId,
                                          ),
                                    child: Text(
                                      controller.isLoading(
                                            widget.conversationId,
                                          )
                                          ? 'Đang tải…'
                                          : 'Tải tin nhắn cũ',
                                    ),
                                  ),
                                )
                              : const SizedBox(height: 8);
                        }
                        final message = messages[index];
                        final own = message.senderId == controller.accountId;
                        return Align(
                          key: message.id == null
                              ? ValueKey(message.clientMessageId)
                              : _rows.putIfAbsent(
                                  message.id!,
                                  () => GlobalKey(),
                                ),
                          alignment: own
                              ? Alignment.centerRight
                              : Alignment.centerLeft,
                          child: Container(
                            constraints: const BoxConstraints(maxWidth: 320),
                            margin: const EdgeInsets.symmetric(vertical: 5),
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: own
                                  ? Theme.of(context)
                                        .colorScheme
                                        .primaryContainer
                                  : Theme.of(context)
                                        .colorScheme
                                        .surfaceContainerHighest,
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(message.content),
                                if (own)
                                  Text(
                                    _status(message.status),
                                    style: Theme.of(context)
                                        .textTheme
                                        .labelSmall,
                                  ),
                                if (own &&
                                    message.status == MessageStatus.failed)
                                  TextButton(
                                    onPressed: () => controller.retry(
                                      message.clientMessageId,
                                    ),
                                    child: const Text('Thử lại'),
                                  ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                ),
                SafeArea(
                  top: false,
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _text,
                            maxLength: 5000,
                            minLines: 1,
                            maxLines: 4,
                            decoration: const InputDecoration(
                              hintText: 'Nhập tin nhắn…',
                              counterText: '',
                            ),
                            onSubmitted: (_) => _send(),
                          ),
                        ),
                        IconButton(
                          tooltip: 'Gửi tin nhắn',
                          onPressed: _sending ? null : _send,
                          icon: const Icon(Icons.send),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
    );
  }

  @override
  void dispose() {
    chatRouteObserver.unsubscribe(this);
    WidgetsBinding.instance.removeObserver(this);
    controller.removeListener(_changed);
    if (_current) controller.setRoomVisible(null);
    _text.dispose();
    _scroll.dispose();
    super.dispose();
  }
}

String _status(MessageStatus status) => switch (status) {
  MessageStatus.pending => 'Đang chờ gửi',
  MessageStatus.failed => 'Gửi thất bại',
  MessageStatus.sent => 'Đã gửi',
  MessageStatus.delivered => 'Đã nhận',
  MessageStatus.read => 'Đã đọc',
};

class _ConnectionBar extends StatelessWidget {
  final ChatController controller;
  const _ConnectionBar({required this.controller});
  @override
  Widget build(BuildContext context) => Material(
    color: Theme.of(context).colorScheme.surfaceContainerLow,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: Row(
        children: [
          Expanded(
            child: Text(
              '${controller.isDemo ? 'MÔ PHỎNG · ' : ''}${switch (controller.connection) {
                ChatConnection.connected => 'Đã kết nối',
                ChatConnection.connecting => 'Đang kết nối…',
                ChatConnection.offline => 'Ngoại tuyến',
                ChatConnection.disconnected => 'Mất kết nối',
              }}',
              style: Theme.of(context).textTheme.labelMedium,
            ),
          ),
          if (controller.isDemo)
            IconButton(
              tooltip: controller.connection == ChatConnection.offline
                  ? 'Kết nối lại mô phỏng'
                  : 'Mất mạng mô phỏng',
              onPressed: () => controller.setDemoOffline(
                controller.connection != ChatConnection.offline,
              ),
              icon: Icon(
                controller.connection == ChatConnection.offline
                    ? Icons.wifi_off
                    : Icons.wifi,
              ),
            ),
          if (controller.connection != ChatConnection.connected)
            TextButton(
              onPressed: () =>
                  controller.isDemo &&
                      controller.connection == ChatConnection.offline
                  ? controller.setDemoOffline(false)
                  : controller.reconnect(),
              child: const Text('Kết nối lại'),
            ),
        ],
      ),
    ),
  );
}

class _ErrorBanner extends StatelessWidget {
  final String message;
  const _ErrorBanner(this.message);
  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(8),
    color: Theme.of(context).colorScheme.errorContainer,
    child: Text(message, maxLines: 2, overflow: TextOverflow.ellipsis),
  );
}
