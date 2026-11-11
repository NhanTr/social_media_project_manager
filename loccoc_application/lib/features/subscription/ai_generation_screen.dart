import 'dart:async';

import 'package:flutter/material.dart';

import 'subscription_controller.dart';
import 'subscription_models.dart';

class AiGenerationScreen extends StatefulWidget {
  final SubscriptionController controller;
  const AiGenerationScreen({super.key, required this.controller});

  @override
  State<AiGenerationScreen> createState() => _AiGenerationScreenState();
}

class _AiGenerationScreenState extends State<AiGenerationScreen>
    with WidgetsBindingObserver {
  final _prompt = TextEditingController();
  AiGeneration? _generation;
  SubscriptionController get c => widget.controller;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(c.refresh());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(c.refresh());
  }

  Future<void> _generate() async {
    final job = await c.generateAi(_prompt.text);
    if (mounted && job != null) setState(() => _generation = job);
  }

  Future<void> _refreshJob() async {
    final job = await c.loadAi(_generation!.id);
    if (mounted && job != null) setState(() => _generation = job);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _prompt.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: c,
    builder: (context, _) => Scaffold(
      appBar: AppBar(
        title: const Text('Tạo ảnh AI'),
        actions: [
          IconButton(
            tooltip: 'Làm mới quyền lợi',
            onPressed: c.busy || c.refreshing ? null : c.refresh,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text(
            'Bản thử nghiệm: ảnh trả về hiện do dịch vụ mô phỏng cung cấp.',
          ),
          const SizedBox(height: 16),
          Text(
            c.canUseAi
                ? 'Tài khoản có quyền tạo ảnh AI'
                : 'AI đang khóa — hãy kiểm tra gói và làm mới quyền lợi.',
          ),
          if (c.busy || c.refreshing) const LinearProgressIndicator(),
          if (c.error != null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text(c.error!),
            ),
          const SizedBox(height: 16),
          TextField(
            controller: _prompt,
            maxLength: 1000,
            minLines: 2,
            maxLines: 5,
            enabled: !c.busy,
            onChanged: (_) => setState(() {}),
            decoration: const InputDecoration(
              labelText: 'Mô tả ảnh',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          FilledButton(
            key: const Key('generate-ai'),
            onPressed:
                c.canUseAi &&
                    !c.busy &&
                    !c.refreshing &&
                    _prompt.text.trim().isNotEmpty &&
                    _prompt.text.trim().length <= 1000
                ? _generate
                : null,
            child: const Text('Tạo ảnh thử nghiệm'),
          ),
          if (_generation case final job?) ...[
            const SizedBox(height: 20),
            Text('Yêu cầu #${job.id}'),
            Text(switch (job.status) {
              'SUCCESS' => 'Ảnh đã sẵn sàng',
              'FAILED' => 'Tạo ảnh thất bại',
              'PROCESSING' => 'Đang xử lý',
              _ => 'Đang chờ xử lý',
            }),
            if (job.mediaUrl != null && c.canUseAi)
              Image.network(
                job.mediaUrl.toString(),
                height: 280,
                fit: BoxFit.contain,
                errorBuilder: (_, _, _) =>
                    const Text('Chưa tải được ảnh. Vui lòng thử lại.'),
              ),
            OutlinedButton(
              onPressed: !c.busy && !c.refreshing && c.canUseAi
                  ? _refreshJob
                  : null,
              child: const Text('Kiểm tra trạng thái ảnh'),
            ),
          ],
        ],
      ),
    ),
  );
}
