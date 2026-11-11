import 'package:flutter/material.dart';

import '../notifications/notification_center_screen.dart';
import '../notifications/notification_controller.dart';

/// Keeps foreground notifications above the current route, including a room or
/// detail page pushed after the shell. Session disposal removes the overlay.
class NotificationBannerOverlay {
  NotificationBannerOverlay({required this.controller, required this.onOpen});
  final NotificationController controller;
  final OpenNotificationTarget onOpen;
  OverlayState? _overlay;
  OverlayEntry? _entry;
  bool _foreground = true;
  bool _scheduled = false;
  bool _disposed = false;

  void attach(OverlayState overlay) {
    if (_disposed || identical(overlay, _overlay)) return;
    controller.removeListener(refresh);
    _entry?.remove();
    _entry?.dispose();
    _entry = null;
    _overlay = overlay;
    controller.addListener(refresh);
    refresh();
  }

  void setForeground(bool value) {
    _foreground = value;
    refresh();
  }

  void refresh() {
    if (_disposed || _scheduled) return;
    _scheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scheduled = false;
      if (_disposed) return;
      final overlay = _overlay;
      if (overlay == null || !overlay.mounted) return;
      if (!_foreground || controller.foregroundNotification == null) {
        _entry?.remove();
        _entry?.dispose();
        _entry = null;
        return;
      }
      if (_entry != null) {
        _entry!.markNeedsBuild();
        return;
      }
      _entry = OverlayEntry(
        builder: (context) => Positioned(
          left: 8,
          right: 8,
          top: MediaQuery.paddingOf(context).top + 8,
          child: Material(
            elevation: 8,
            clipBehavior: Clip.antiAlias,
            borderRadius: BorderRadius.circular(12),
            child: NotificationForegroundBanner(
              key: const Key('s3-foreground-banner'),
              controller: controller,
              onOpen: onOpen,
            ),
          ),
        ),
      );
      overlay.insert(_entry!);
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    controller.removeListener(refresh);
    _entry?.remove();
    _entry?.dispose();
    _entry = null;
    _overlay = null;
  }
}
