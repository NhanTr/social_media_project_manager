import 'dart:convert';
import 'dart:math';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../../core/models/post_models.dart';
import '../../core/network/posts_api.dart';
import 'composer_adapters.dart';
import 'composer_image.dart';

enum ComposerStage {
  editing,
  preparing,
  creatingUpload,
  uploading,
  finalizing,
  creatingPost,
  uncertain,
  cancelled,
  published,
}

class _Cancelled implements Exception {}

class ComposerController extends ChangeNotifier {
  ComposerController({
    required this.api,
    required this.accountId,
    ComposerDraftStore? store,
    ComposerPhotoPicker? picker,
    PhotoProcessor? processor,
    PhotoUploadTransport? upload,
  }) : _store = store ?? LocalComposerDraftStore(accountId),
       _picker = picker ?? NativeComposerPhotoPicker(),
       _processor = processor ?? const LocalPhotoProcessor(),
       _upload = upload ?? DioPhotoUploadTransport();
  final PostsApi api;
  final int accountId;
  final ComposerDraftStore _store;
  final ComposerPhotoPicker _picker;
  final PhotoProcessor _processor;
  final PhotoUploadTransport _upload;
  Uint8List? _source;
  Uint8List? get source => _source;
  PreparedPhoto? prepared;
  PhotoSettings settings = const PhotoSettings();
  String caption = '';
  String clientRequestId = _uuid();
  ComposerStage stage = ComposerStage.editing;
  String? error;
  bool busy = false,
      initialized = false,
      _cancelled = false,
      _disposed = false,
      _publishSent = false,
      _uploaded = false;
  bool get canEdit => initialized && !busy && !_publishSent && receipt == null;
  bool get uncertain => _publishSent && receipt == null;
  double progress = 0;
  MediaUploadSession? _session;
  UploadedMedia? _media;
  String? _frozenCaption, _frozenFilter;
  PostModel? receipt;
  UploadAbort? _abort;
  Future<void> _writes = Future<void>.value();

  void _emit() {
    if (!_disposed) notifyListeners();
  }

  Future<void> _persist() {
    final snapshot = <String, dynamic>{
      'version': 1,
      'accountId': accountId,
      'clientRequestId': clientRequestId,
      'caption': caption,
      'settings': settings.toJson(),
      'stage': stage.name,
      'source': _source == null ? null : base64Encode(_source!),
      'prepared': prepared == null
          ? null
          : {
              'bytes': base64Encode(prepared!.bytes),
              'width': prepared!.width,
              'height': prepared!.height,
            },
      'session': _session == null
          ? null
          : {
              'uploadId': _session!.uploadId,
              'presignedUrl': _session!.presignedUrl,
            },
      'uploaded': _uploaded,
      'media': _media?.toJson(),
      'frozenCaption': _frozenCaption,
      'frozenFilter': _frozenFilter,
      'publishSent': _publishSent,
      'receipt': receipt?.toJson(),
    };
    final write = _writes.then((_) => _store.write(snapshot));
    _writes = write.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return write;
  }

  Future<void> initialize() async {
    if (initialized || busy) return;
    busy = true;
    _emit();
    try {
      final json = await _store.read();
      if (json != null && json['accountId'] == accountId) {
        clientRequestId = json['clientRequestId'] as String;
        caption = json['caption'] as String? ?? '';
        settings = PhotoSettings.fromJson(
          Map<String, dynamic>.from(json['settings'] as Map),
        );
        if (json['source'] != null) {
          _source = base64Decode(json['source'] as String);
        }
        final photo = json['prepared'] as Map?;
        if (photo != null) {
          prepared = PreparedPhoto(
            base64Decode(photo['bytes'] as String),
            photo['width'] as int,
            photo['height'] as int,
          );
        }
        final session = json['session'] as Map?;
        if (session != null) {
          _session = MediaUploadSession(
            uploadId: session['uploadId'] as int,
            presignedUrl: session['presignedUrl'] as String,
          );
        }
        if (json['media'] != null) {
          _media = UploadedMedia.fromJson(
            Map<String, dynamic>.from(json['media'] as Map),
          );
        }
        _uploaded = json['uploaded'] == true;
        _publishSent = json['publishSent'] == true;
        _frozenCaption = json['frozenCaption'] as String?;
        _frozenFilter = json['frozenFilter'] as String?;
        if (json['receipt'] != null) {
          receipt = PostModel.fromJson(
            Map<String, dynamic>.from(json['receipt'] as Map),
          );
        }
        stage = receipt != null
            ? ComposerStage.published
            : _publishSent
            ? ComposerStage.uncertain
            : ComposerStage.editing;
      }
      final recovered = await _picker.recover();
      if (recovered != null && !_publishSent && receipt == null) {
        _source = recovered;
        _invalidatePrepared();
        await _persist();
      }
    } catch (e) {
      error = 'Không khôi phục được bản nháp: $e';
    } finally {
      initialized = true;
      busy = false;
      _emit();
    }
  }

  void _invalidatePrepared() {
    if (prepared != null || _session != null) clientRequestId = _uuid();
    prepared = null;
    _session = null;
    _media = null;
    _uploaded = false;
    _frozenCaption = null;
    _frozenFilter = null;
    stage = ComposerStage.editing;
  }

  Future<bool> setCaption(String value) async {
    if (!canEdit) return false;
    caption = value;
    _invalidatePrepared();
    error = null;
    _emit();
    try {
      await _persist();
      return true;
    } catch (e) {
      error = 'Không lưu được bản nháp: $e';
      _emit();
      return false;
    }
  }

  Future<bool> setSettings(PhotoSettings value) async {
    if (!canEdit) return false;
    settings = value;
    _invalidatePrepared();
    error = null;
    _emit();
    try {
      await _persist();
      return true;
    } catch (e) {
      error = 'Không lưu được bản nháp: $e';
      _emit();
      return false;
    }
  }

  Future<void> pick({required bool camera}) async {
    if (!canEdit) return;
    busy = true;
    error = null;
    _emit();
    try {
      final bytes = await _picker.pick(camera: camera);
      if (!_disposed && bytes != null) {
        _source = bytes;
        _invalidatePrepared();
        await _persist();
      }
    } catch (e) {
      error = '$e';
    } finally {
      busy = false;
      _emit();
    }
  }

  Future<PreparedPhoto?> preview() async {
    final bytes = _source;
    return bytes == null ? null : _processor.process(bytes, settings);
  }

  void _guard() {
    if (_cancelled || _disposed) throw _Cancelled();
  }

  Future<void> _stage(ComposerStage value) async {
    stage = value;
    _emit();
    await _persist();
    _guard();
  }

  Future<PostModel?> publish() async {
    if (busy || !initialized) return null;
    if (receipt != null) {
      try {
        await _persist();
        return receipt;
      } catch (e) {
        error = 'Không lưu được xác nhận bài đã đăng: $e';
        _emit();
        return null;
      }
    }
    if (_source == null) {
      error = 'Hãy chọn một ảnh trước khi đăng.';
      _emit();
      return null;
    }
    if (caption.length > 2000) {
      error = 'Chú thích tối đa 2000 ký tự.';
      _emit();
      return null;
    }
    final reconciling = _publishSent;
    busy = true;
    _cancelled = false;
    error = null;
    _abort = UploadAbort();
    _emit();
    try {
      if (prepared == null) {
        await _stage(ComposerStage.preparing);
        final photo = await _processor.process(_source!, settings);
        _guard();
        if (photo.bytes.isEmpty ||
            photo.bytes.length >= 5 * 1024 * 1024 ||
            max(photo.width, photo.height) > 2048) {
          throw const PhotoException('Ảnh phải dưới 5 MiB và tối đa 2048 px.');
        }
        prepared = photo;
        _frozenCaption = caption;
        _frozenFilter = settings.filter.name;
        await _persist();
        _guard();
      }
      if (_session == null) {
        await _stage(ComposerStage.creatingUpload);
        _session = await api.createUpload(prepared!.bytes.length);
        await _persist();
        _guard();
      }
      if (!_uploaded && _media == null) {
        await _stage(ComposerStage.uploading);
        try {
          await _upload.put(_session!.presignedUrl, prepared!.bytes, _abort!, (
            sent,
            total,
          ) {
            if (!_cancelled && !_disposed) {
              progress = total > 0 ? (sent / total).clamp(0, 1) : 0;
              _emit();
            }
          });
        } on DioException catch (e) {
          if ([401, 403, 404, 410].contains(e.response?.statusCode)) {
            _session = null;
            await _persist();
          }
          rethrow;
        }
        _guard();
        _uploaded = true;
        await _persist();
        _guard();
      }
      if (_media == null) {
        await _stage(ComposerStage.finalizing);
        try {
          _media = await api.finalizeUpload(_session!.uploadId);
        } on PostApiException catch (e) {
          if (e.code == 'UPLOAD_SESSION_EXPIRED' ||
              e.statusCode == 404 ||
              e.statusCode == 410) {
            _session = null;
            _uploaded = false;
            await _persist();
          }
          rethrow;
        }
        await _persist();
        _guard();
      }
      // This durable marker makes a killed process replay precisely the same request.
      _publishSent = true;
      await _stage(ComposerStage.creatingPost);
      final post = await api.publish(
        clientRequestId: clientRequestId,
        uploadId: _session!.uploadId,
        media: _media!,
        caption: _frozenCaption!,
        filterApplied: _frozenFilter!,
      );
      receipt = post;
      stage = ComposerStage.published;
      await _persist();
      return _cancelled || _disposed ? null : post;
    } on _Cancelled {
      return null;
    } catch (e) {
      // Only the first attempt can prove that no earlier ambiguous request committed.
      if (!reconciling &&
          _publishSent &&
          receipt == null &&
          e is PostApiException &&
          e.statusCode == 400 &&
          e.code == 'VALIDATION_FAILED') {
        _publishSent = false;
      }
      stage = _publishSent ? ComposerStage.uncertain : ComposerStage.editing;
      error = _publishSent
          ? 'Chưa xác định được kết quả đăng. Thử lại để xác nhận cùng bài viết; nội dung đang được khóa. $e'
          : 'Không đăng được ảnh. Bản nháp đã được giữ lại. $e';
      try {
        await _persist();
      } catch (_) {
        error = 'Không lưu được bản nháp. $e';
      }
      return null;
    } finally {
      busy = false;
      _emit();
    }
  }

  Future<void> cancel() async {
    _cancelled = true;
    _abort?.cancel();
    stage = uncertain ? ComposerStage.uncertain : ComposerStage.cancelled;
    error = uncertain
        ? 'Yêu cầu đăng đã gửi; bài viết có thể đã được tạo. Thử lại để xác nhận, không tạo bài mới.'
        : 'Đã dừng. Bản nháp được giữ để thử lại.';
    _emit();
    try {
      await _persist();
    } catch (e) {
      error = '$error Không lưu được trạng thái dừng: $e';
      _emit();
    }
  }

  Future<bool> discard() async {
    if (busy || uncertain) return false;
    await _writes;
    await _store.remove();
    _source = null;
    caption = '';
    receipt = null;
    settings = const PhotoSettings();
    _invalidatePrepared();
    clientRequestId = _uuid();
    error = null;
    _emit();
    return true;
  }

  Future<void> acknowledge() async {
    if (receipt == null) return;
    await _writes;
    await _store.remove();
  }

  @override
  void dispose() {
    _disposed = true;
    _cancelled = true;
    _abort?.cancel();
    super.dispose();
  }
}

String _uuid() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  final h = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}';
}
