import 'dart:async';

import 'package:flutter/material.dart';

import '../../core/models/post_models.dart';
import '../../core/network/posts_api.dart';
import 'composer_controller.dart';
import 'composer_image.dart';

class ComposerScreen extends StatefulWidget {
  const ComposerScreen({
    super.key,
    required this.api,
    required this.accountId,
    this.controller,
  });
  final PostsApi api;
  final int accountId;
  final ComposerController? controller;
  @override
  State<ComposerScreen> createState() => _ComposerScreenState();
}

class _ComposerScreenState extends State<ComposerScreen> {
  late final ComposerController _controller;
  final _caption = TextEditingController();
  Future<PreparedPhoto?>? _preview;
  int _previewGeneration = 0;
  bool _submitting = false;
  @override
  void initState() {
    super.initState();
    _controller =
        widget.controller ??
        ComposerController(api: widget.api, accountId: widget.accountId);
    unawaited(_initialize());
  }

  Future<void> _initialize() async {
    await _controller.initialize();
    if (!mounted) return;
    _caption.text = _controller.caption;
    _updatePreview();
  }

  void _updatePreview() {
    if (mounted) {
      setState(() {
        _previewGeneration++;
        _preview = _controller.preview();
      });
    }
  }

  Future<void> _pick(bool camera) async {
    await _controller.pick(camera: camera);
    if (mounted) _updatePreview();
  }

  Future<void> _settings({
    bool? square,
    double? x,
    double? y,
    PhotoFilter? filter,
  }) async {
    final old = _controller.settings;
    await _controller.setSettings(
      PhotoSettings(
        square: square ?? old.square,
        cropX: x ?? old.cropX,
        cropY: y ?? old.cropY,
        filter: filter ?? old.filter,
      ),
    );
    _updatePreview();
  }

  Future<void> _publish() async {
    if (_submitting) return;
    setState(() => _submitting = true);
    final post = await _controller.publish();
    if (!mounted) return;
    if (post == null) {
      setState(() => _submitting = false);
      return;
    }
    try {
      await _controller.acknowledge();
    } catch (_) {
      /* Durable receipt remains recoverable if cleanup fails. */
    }
    if (mounted) Navigator.of(context).pop<PostModel>(post);
  }

  Future<void> _discard() async {
    final yes = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Bỏ bản nháp?'),
        content: const Text(
          'Ảnh và nội dung đã lưu trên thiết bị sẽ được xóa.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Giữ lại'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Bỏ bản nháp'),
          ),
        ],
      ),
    );
    if (yes != true) return;
    try {
      if (await _controller.discard() && mounted) {
        _caption.clear();
        _updatePreview();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Không xóa được bản nháp: $e')));
      }
    }
  }

  @override
  void dispose() {
    _caption.dispose();
    if (widget.controller == null) _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _controller,
    builder: (context, _) {
      final c = _controller;
      return PopScope(
        canPop: !c.busy && !_submitting,
        onPopInvokedWithResult: (didPop, result) {
          if (!didPop) unawaited(c.cancel());
        },
        child: Scaffold(
          appBar: AppBar(
            title: const Text('Chia sẻ khoảnh khắc'),
            actions: [
              IconButton(
                tooltip: 'Bỏ bản nháp',
                onPressed: c.canEdit ? _discard : null,
                icon: const Icon(Icons.delete_outline),
              ),
            ],
          ),
          body: !c.initialized
              ? const Center(child: CircularProgressIndicator())
              : ListView(
                  padding: const EdgeInsets.all(20),
                  children: [
                    const Text(
                      'Bản nháp tự động lưu trên thiết bị.',
                      style: TextStyle(color: Colors.grey),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: c.canEdit ? () => _pick(true) : null,
                            icon: const Icon(Icons.camera_alt_outlined),
                            label: const Text('Máy ảnh'),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: c.canEdit ? () => _pick(false) : null,
                            icon: const Icon(Icons.photo_library_outlined),
                            label: const Text('Thư viện'),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    if (c.source == null)
                      const SizedBox(
                        height: 200,
                        child: Center(child: Text('Chọn ảnh để bắt đầu')),
                      )
                    else
                      FutureBuilder<PreparedPhoto?>(
                        key: ValueKey(_previewGeneration),
                        future: _preview,
                        builder: (context, snapshot) {
                          if (snapshot.hasError) {
                            return SizedBox(
                              height: 160,
                              child: Center(child: Text('${snapshot.error}')),
                            );
                          }
                          final photo = snapshot.data;
                          if (photo == null) {
                            return const SizedBox(
                              height: 200,
                              child: Center(child: CircularProgressIndicator()),
                            );
                          }
                          return SizedBox(
                            height: 300,
                            child: Image.memory(
                              photo.bytes,
                              fit: BoxFit.contain,
                              gaplessPlayback: true,
                              errorBuilder: (_, error, stack) => const Center(
                                child: Text(
                                  'Không hiển thị được ảnh xem trước.',
                                ),
                              ),
                            ),
                          );
                        },
                      ),
                    const SizedBox(height: 16),
                    SegmentedButton<bool>(
                      segments: const [
                        ButtonSegment(value: false, label: Text('Tỉ lệ gốc')),
                        ButtonSegment(value: true, label: Text('Vuông')),
                      ],
                      selected: {c.settings.square},
                      onSelectionChanged: c.canEdit
                          ? (value) => _settings(square: value.first)
                          : null,
                    ),
                    if (c.settings.square) ...[
                      const SizedBox(height: 8),
                      const Text('Vị trí cắt ngang'),
                      Slider(
                        value: c.settings.cropX,
                        onChanged: c.canEdit
                            ? (value) => _settings(x: value)
                            : null,
                      ),
                      const Text('Vị trí cắt dọc'),
                      Slider(
                        value: c.settings.cropY,
                        onChanged: c.canEdit
                            ? (value) => _settings(y: value)
                            : null,
                      ),
                    ],
                    const SizedBox(height: 12),
                    SegmentedButton<PhotoFilter>(
                      segments: const [
                        ButtonSegment(
                          value: PhotoFilter.original,
                          label: Text('Gốc'),
                        ),
                        ButtonSegment(
                          value: PhotoFilter.mono,
                          label: Text('Đen trắng'),
                        ),
                        ButtonSegment(
                          value: PhotoFilter.warm,
                          label: Text('Ấm'),
                        ),
                      ],
                      selected: {c.settings.filter},
                      onSelectionChanged: c.canEdit
                          ? (value) => _settings(filter: value.first)
                          : null,
                    ),
                    const SizedBox(height: 20),
                    TextField(
                      controller: _caption,
                      enabled: c.canEdit,
                      maxLines: 4,
                      maxLength: 2000,
                      decoration: const InputDecoration(
                        labelText: 'Chú thích',
                        hintText: 'Kể về khoảnh khắc này',
                        border: OutlineInputBorder(),
                      ),
                      onChanged: (value) => unawaited(c.setCaption(value)),
                    ),
                    if (c.busy) ...[
                      const SizedBox(height: 12),
                      Text(_stageLabel(c.stage)),
                      const SizedBox(height: 8),
                      LinearProgressIndicator(
                        value: c.stage == ComposerStage.uploading
                            ? c.progress
                            : null,
                      ),
                      if (c.stage == ComposerStage.uploading)
                        Text('${(c.progress * 100).round()}%'),
                      TextButton(
                        onPressed: () => unawaited(c.cancel()),
                        child: const Text('Dừng đăng, giữ bản nháp'),
                      ),
                    ],
                    if (c.error != null) ...[
                      const SizedBox(height: 12),
                      Text(
                        c.error!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ],
                    const SizedBox(height: 16),
                    FilledButton.icon(
                      onPressed: c.busy || _submitting ? null : _publish,
                      icon: const Icon(Icons.send_outlined),
                      label: Text(
                        c.receipt != null
                            ? 'Xem bài đã đăng'
                            : c.uncertain
                            ? 'Thử lại để xác nhận'
                            : c.error != null
                            ? 'Thử đăng lại'
                            : 'Đăng ảnh',
                      ),
                    ),
                  ],
                ),
        ),
      );
    },
  );
  String _stageLabel(ComposerStage stage) => switch (stage) {
    ComposerStage.preparing => 'Đang xử lý ảnh…',
    ComposerStage.creatingUpload => 'Đang tạo phiên tải ảnh…',
    ComposerStage.uploading => 'Đang tải ảnh…',
    ComposerStage.finalizing => 'Đang xác nhận ảnh…',
    ComposerStage.creatingPost => 'Đang đăng bài…',
    ComposerStage.uncertain => 'Đang xác nhận kết quả…',
    ComposerStage.cancelled => 'Đã yêu cầu dừng…',
    ComposerStage.published => 'Đã đăng ảnh',
    _ => 'Đang lưu bản nháp…',
  };
}
