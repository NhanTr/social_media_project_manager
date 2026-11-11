import 'dart:async';

import 'package:flutter/material.dart';

import 'ai_generation_screen.dart';
import 'subscription_controller.dart';
import 'subscription_models.dart';

class SubscriptionScreen extends StatefulWidget {
  final SubscriptionController controller;
  const SubscriptionScreen({super.key, required this.controller});

  @override
  State<SubscriptionScreen> createState() => _SubscriptionScreenState();
}

class _SubscriptionScreenState extends State<SubscriptionScreen>
    with WidgetsBindingObserver {
  SubscriptionController get c => widget.controller;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(c.refresh());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(c.reconcileCheckout());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  String _date(DateTime value) {
    final local = value.toLocal();
    return '${local.day}/${local.month}/${local.year}';
  }

  Future<void> _cancel() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Hủy gia hạn Plus?'),
        content: const Text(
          'Gói sẽ ngừng gia hạn vào cuối kỳ. Quyền lợi hiện tại được giữ theo thời hạn tài khoản.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Giữ gia hạn'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Xác nhận hủy'),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) await c.cancelAtPeriodEnd();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: c,
    builder: (context, _) {
      final state = c.snapshot;
      return Scaffold(
        appBar: AppBar(
          title: const Text('Gói và quyền lợi'),
          actions: [
            IconButton(
              tooltip: 'Làm mới quyền lợi',
              onPressed: c.busy || c.refreshing ? null : c.reconcileCheckout,
              icon: const Icon(Icons.refresh),
            ),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Text(
              'Chọn gói phù hợp với bạn',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 8),
            const Text(
              'Quyền lợi được xác nhận từ tài khoản. Sau thanh toán, quay lại ứng dụng để làm mới.',
            ),
            if (c.refreshing || c.busy) ...[
              const SizedBox(height: 16),
              const LinearProgressIndicator(),
            ],
            if (c.error != null) _notice(c.error!, Icons.cloud_off_outlined),
            if (state == null && !c.refreshing)
              _notice(
                'Thông tin gói hiện chưa khả dụng. Hãy thử làm mới.',
                Icons.info_outline,
              ),
            if (state != null) _account(state),
            if (c.paymentPending)
              _notice(
                'Đang chờ xác nhận thanh toán. Quyền lợi sẽ cập nhật khi thanh toán được xác nhận. Bạn có thể làm mới lại sau.',
                Icons.hourglass_top,
              ),
            if (c.paymentFailed)
              _notice(
                'Thanh toán chưa thành công. Tài khoản vẫn sử dụng được; kiểm tra quyền lợi và thử lại khi sẵn sàng.',
                Icons.error_outline,
              ),
            for (final plan in c.plans) _plan(plan, state?.tier == plan.tier),
            if (c.plans.any((p) => p.sandbox))
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  'Sandbox — giá thử nghiệm, không phải giá bán chính thức. Chỉ sử dụng thông tin thanh toán thử nghiệm.',
                ),
              ),
            if (state?.tier == 'FREE')
              FilledButton(
                key: const Key('checkout'),
                onPressed: c.canCheckout ? c.startCheckout : null,
                child: Text(
                  c.paymentPending
                      ? 'Mở lại thanh toán sandbox'
                      : 'Nâng cấp Plus · Sandbox',
                ),
              ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: c.busy || c.refreshing ? null : c.reconcileCheckout,
              icon: const Icon(Icons.sync),
              label: const Text('Khôi phục / Làm mới quyền lợi'),
            ),
            const SizedBox(height: 20),
            Text(
              c.canUseAi ? 'AI đã được mở cho tài khoản' : 'AI đang khóa',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            const Text(
              'Tạo ảnh AI chỉ khả dụng khi tài khoản có quyền còn hiệu lực. Mất kết nối hoặc quyền hết hạn cần được xác nhận lại.',
            ),
            const SizedBox(height: 12),
            FilledButton(
              key: const Key('open-ai'),
              onPressed: c.canUseAi && !c.busy && !c.refreshing
                  ? () => Navigator.push(
                      context,
                      MaterialPageRoute<void>(
                        builder: (_) => AiGenerationScreen(controller: c),
                      ),
                    )
                  : null,
              child: const Text('Mở công cụ AI'),
            ),
            if (state?.tier == 'PLUS') ...[
              const SizedBox(height: 20),
              OutlinedButton(
                key: const Key('cancel-renewal'),
                onPressed: c.canCancel ? _cancel : null,
                child: const Text('Hủy gia hạn vào cuối kỳ'),
              ),
            ],
          ],
        ),
      );
    },
  );

  Widget _account(SubscriptionSnapshot state) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Gói hiện tại: ${state.tier == 'PLUS' ? 'Plus' : 'Free'}',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(switch (state.status) {
            'ACTIVE' => 'Đang hoạt động',
            'GRACE' => 'Đang trong thời gian gia hạn thanh toán',
            'PAST_DUE' => 'Cần cập nhật thanh toán',
            'CANCELED' => 'Đã hủy gói',
            'EXPIRED' => 'Gói đã hết hạn',
            _ => 'Chưa hoàn tất đăng ký',
          }),
          if (state.currentPeriodEnd != null)
            Text(
              '${state.cancelAtPeriodEnd ? 'Quyền lợi đến' : 'Kỳ hiện tại kết thúc'}: ${_date(state.currentPeriodEnd!)}',
            ),
          if (state.cancelAtPeriodEnd)
            const Text(
              'Đã hủy gia hạn. Gói sẽ không tự gia hạn ở kỳ tiếp theo.',
            ),
          if (c.error != null)
            const Text(
              'Thông tin hiển thị có thể đã cũ. Quyền AI đang khóa cho đến khi làm mới thành công.',
            ),
        ],
      ),
    ),
  );

  Widget _plan(SubscriptionPlan plan, bool selected) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${plan.name}${selected ? ' · Gói hiện tại' : ''}',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          Text(
            '${plan.priceLabel}${plan.sandbox ? ' · Sandbox' : ''}',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          for (final benefit in plan.benefits)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text('✓ $benefit'),
            ),
        ],
      ),
    ),
  );

  Widget _notice(String text, IconData icon) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 12),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon),
        const SizedBox(width: 10),
        Expanded(child: Text(text)),
      ],
    ),
  );
}
