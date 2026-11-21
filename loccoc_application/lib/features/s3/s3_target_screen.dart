import 'package:flutter/material.dart';

import '../notifications/notification_models.dart';

/// Read-only destinations for the explicitly labelled local S3 preview.
/// Real adapters must resolve ownership/visibility through their domain service.
class S3DemoTargetScreen extends StatelessWidget {
  const S3DemoTargetScreen({super.key, required this.target});
  final NotificationTarget target;

  static (String, String, IconData)? details(NotificationTarget target) {
    return switch ((target.kind, target.id)) {
      (NotificationTargetKind.friendRequest, 201) => (
        'Lời mời kết bạn',
        'Minh muốn kết nối với bạn.',
        Icons.person_add_outlined,
      ),
      (NotificationTargetKind.profile, 2) => (
        'Hồ sơ bạn bè',
        'Minh · Bạn bè\nCùng chia sẻ những khoảnh khắc mỗi ngày.',
        Icons.person_outline,
      ),
      (NotificationTargetKind.post, 301) => (
        'Chi tiết ảnh',
        'Khoảnh khắc buổi sáng\nMột bài đăng mẫu từ bạn bè.',
        Icons.photo_outlined,
      ),
      (NotificationTargetKind.subscription, 401) => (
        'Gói đăng ký',
        'LocCoc Plus\nThông tin gói mẫu, không có thanh toán thực tế.',
        Icons.workspace_premium_outlined,
      ),
      (NotificationTargetKind.report, 501) => (
        'Kết quả báo cáo',
        'Báo cáo mẫu đã được xem xét. Cảm ơn bạn đã giúp cộng đồng an toàn hơn.',
        Icons.fact_check_outlined,
      ),
      _ => null,
    };
  }

  @override
  Widget build(BuildContext context) {
    final detail = details(target);
    if (detail == null) return const S3UnavailableTargetScreen();
    return Scaffold(
      appBar: AppBar(title: Text(detail.$1)),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const Align(
            alignment: Alignment.centerLeft,
            child: Chip(label: Text('Dữ liệu mô phỏng')),
          ),
          const SizedBox(height: 24),
          Icon(
            detail.$3,
            size: 72,
            color: Theme.of(context).colorScheme.primary,
          ),
          const SizedBox(height: 24),
          Text(detail.$1, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 12),
          Text(detail.$2, style: Theme.of(context).textTheme.bodyLarge),
          const SizedBox(height: 24),
          const Text(
            'Thông tin trong bản xem trước chỉ được lưu trên thiết bị này.',
          ),
        ],
      ),
    );
  }
}

class S3UnavailableTargetScreen extends StatelessWidget {
  const S3UnavailableTargetScreen({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Thông báo')),
    body: Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.link_off, size: 56),
            const SizedBox(height: 16),
            const Text(
              'Nội dung không còn khả dụng',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            const Text(
              'Bạn có thể quay lại để xem các thông báo khác.',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 20),
            FilledButton.tonal(
              onPressed: () => Navigator.of(context).maybePop(),
              child: const Text('Quay lại'),
            ),
          ],
        ),
      ),
    ),
  );
}
