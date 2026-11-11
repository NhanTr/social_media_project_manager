/// Safe, typed destinations. IDs always retain their domain meaning.
enum NotificationTargetKind {
  friendRequest,
  profile,
  post,
  conversation,
  subscription,
  report,
  unavailable,
}

class NotificationTarget {
  final NotificationTargetKind kind;
  final int? id;
  final int? messageId;
  const NotificationTarget._(this.kind, this.id, this.messageId);
  const NotificationTarget.unavailable()
    : this._(NotificationTargetKind.unavailable, null, null);
  bool get isAvailable => kind != NotificationTargetKind.unavailable;

  static NotificationTarget parse({
    required String? type,
    required Object? target,
  }) {
    if (target is! Map) return const NotificationTarget.unavailable();
    if (target['available'] == false ||
        const ['DELETED', 'FORBIDDEN', 'MISSING'].contains(target['status'])) {
      return const NotificationTarget.unavailable();
    }
    final expected = switch (type) {
      'FRIEND_REQUEST' => (
        'FRIEND_REQUEST',
        NotificationTargetKind.friendRequest,
      ),
      'FRIEND_ACCEPT' => ('PROFILE', NotificationTargetKind.profile),
      'NEW_POST' => ('POST', NotificationTargetKind.post),
      'CHAT_MESSAGE' => ('CONVERSATION', NotificationTargetKind.conversation),
      'SUBSCRIPTION_UPDATED' => (
        'SUBSCRIPTION',
        NotificationTargetKind.subscription,
      ),
      'REPORT_RESULT' => ('REPORT', NotificationTargetKind.report),
      _ => null,
    };
    final id = _positiveId(target['id']);
    if (expected == null || target['kind'] != expected.$1 || id == null) {
      return const NotificationTarget.unavailable();
    }
    final message = target['messageId'];
    if (message != null &&
        (_positiveId(message) == null || type != 'CHAT_MESSAGE')) {
      return const NotificationTarget.unavailable();
    }
    return NotificationTarget._(expected.$2, id, message as int?);
  }

  Map<String, dynamic>? toJson() {
    if (!isAvailable) return null;
    return {
      'kind': switch (kind) {
        NotificationTargetKind.friendRequest => 'FRIEND_REQUEST',
        NotificationTargetKind.profile => 'PROFILE',
        NotificationTargetKind.post => 'POST',
        NotificationTargetKind.conversation => 'CONVERSATION',
        NotificationTargetKind.subscription => 'SUBSCRIPTION',
        NotificationTargetKind.report => 'REPORT',
        NotificationTargetKind.unavailable => throw StateError(
          'Unavailable target',
        ),
      },
      'id': id,
      if (messageId != null) 'messageId': messageId,
    };
  }
}

int? _positiveId(Object? value) =>
    value is int &&
        value > 0 &&
        (!const bool.fromEnvironment('dart.library.js_interop') ||
            value <= 9007199254740991)
    ? value
    : null;

/// Used for both foreground/tap data and deferred cold-start data. No URL routing.
class NotificationPushPayload {
  final int? notificationId;
  final String? type;
  final NotificationTarget target;
  const NotificationPushPayload._(this.notificationId, this.type, this.target);

  static NotificationPushPayload parse(Map<String, dynamic> data) {
    int? parseId(Object? value) =>
        value is String && RegExp(r'^[1-9][0-9]*$').hasMatch(value)
        ? _positiveId(int.tryParse(value))
        : null;
    final id = parseId(data['notificationId']);
    final type = data['type'] is String ? data['type'] as String : null;
    if (id == null ||
        type == null ||
        data.values.any((value) => value is! String)) {
      return NotificationPushPayload._(
        id,
        type,
        const NotificationTarget.unavailable(),
      );
    }
    final targetId = parseId(data['targetId']);
    final messageId = parseId(data['messageId']);
    if (data.containsKey('messageId') && messageId == null) {
      return NotificationPushPayload._(
        id,
        type,
        const NotificationTarget.unavailable(),
      );
    }
    return NotificationPushPayload._(
      id,
      type,
      NotificationTarget.parse(
        type: type,
        target: {
          'kind': data['targetKind'],
          'id': targetId,
          'messageId': ?messageId,
        },
      ),
    );
  }
}

class NotificationItem {
  final int id, userId;
  final int? actorUserId, referenceId;
  final String type, title, body;
  final bool read;
  final DateTime createdAt;
  final DateTime? readAt;
  final NotificationTarget target;
  const NotificationItem({
    required this.id,
    required this.userId,
    required this.type,
    required this.title,
    required this.body,
    required this.createdAt,
    required this.target,
    this.read = false,
    this.readAt,
    this.referenceId,
    this.actorUserId,
  });

  NotificationItem withRead(DateTime time) => NotificationItem(
    id: id,
    userId: userId,
    type: type,
    title: title,
    body: body,
    createdAt: createdAt,
    target: target,
    read: true,
    readAt: readAt ?? time,
    referenceId: referenceId,
    actorUserId: actorUserId,
  );
  factory NotificationItem.fromJson(Map<String, dynamic> json) {
    final id = _positiveId(json['id']), userId = _positiveId(json['userId']);
    final createdAt = DateTime.tryParse(
      json['createdAt'] is String ? json['createdAt'] : '',
    );
    if (id == null ||
        userId == null ||
        createdAt == null ||
        json['type'] is! String ||
        (json['title'] != null && json['title'] is! String) ||
        (json['body'] != null && json['body'] is! String) ||
        json['read'] is! bool) {
      throw const FormatException('Invalid notification record');
    }
    return NotificationItem(
      id: id,
      userId: userId,
      type: json['type'],
      title: json['title'] ?? 'Thông báo',
      body: json['body'] ?? '',
      createdAt: createdAt,
      read: json['read'],
      readAt: json['readAt'] is String
          ? DateTime.tryParse(json['readAt'])
          : null,
      target: NotificationTarget.parse(
        type: json['type'],
        target: json['target'],
      ),
      referenceId: _positiveId(json['referenceId']),
      actorUserId: _positiveId(json['actorUserId']),
    );
  }
  Map<String, dynamic> toJson() => {
    'id': id,
    'userId': userId,
    'type': type,
    'title': title,
    'body': body,
    'read': read,
    'createdAt': createdAt.toUtc().toIso8601String(),
    'readAt': readAt?.toUtc().toIso8601String(),
    'target': target.toJson(),
    'referenceId': referenceId,
    'actorUserId': actorUserId,
  };
}

class NotificationPage {
  final List<NotificationItem> items;
  final String? nextCursor;
  const NotificationPage({required this.items, this.nextCursor});
}
