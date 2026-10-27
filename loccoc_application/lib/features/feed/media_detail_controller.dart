import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../core/models/post_models.dart';
import '../../core/network/posts_api.dart';
import 'media_adapters.dart';

class MediaDetailController extends ChangeNotifier {
  MediaDetailController({
    required this.api,
    required this.postId,
    required this.onUnavailable,
    GalleryAdapter? gallery,
    MediaDownloader? downloader,
    bool Function()? isSessionActive,
  }) : gallery = gallery ?? GalGalleryAdapter(),
       downloader = downloader ?? DioMediaDownloader(),
       isSessionActive = isSessionActive ?? (() => true);
  final PostsApi api;
  final int postId;
  final FutureOr<void> Function(int) onUnavailable;
  final GalleryAdapter gallery;
  final MediaDownloader downloader;
  final bool Function() isSessionActive;
  static const maxSaveBytes = 10 * 1024 * 1024;
  PostModel? post;
  bool unavailable = false;
  bool loading = false;
  bool saving = false;
  String? message;
  bool _disposed = false;
  int _generation = 0;
  bool _current(int generation) =>
      !_disposed && generation == _generation && isSessionActive();
  void _emit() {
    if (!_disposed) notifyListeners();
  }

  bool _denied(Object e) =>
      e is MediaAccessException ||
      e is PostApiException && [401, 403, 404].contains(e.statusCode);

  Future<void> _markUnavailable() async {
    final first = !unavailable;
    unavailable = true;
    post = null;
    message = 'Ảnh không còn khả dụng hoặc bạn không có quyền xem.';
    _emit();
    if (first) {
      try {
        await onUnavailable(postId);
      } catch (_) {
        /* Feed already removes visible item before persisting. */
      }
    }
  }

  Future<void> invalidateAccess() async {
    if (_disposed || unavailable) return;
    ++_generation;
    loading = false;
    saving = false;
    await _markUnavailable();
  }

  Future<PostModel> _authorized() async {
    final result = await api.detail(postId);
    if (result.id != postId || result.status != 'ACTIVE') {
      throw const MediaAccessException();
    }
    return result;
  }

  Future<void> load() async {
    if (_disposed || saving || !isSessionActive()) return;
    final generation = ++_generation;
    loading = true;
    post = null;
    message = null;
    _emit();
    try {
      final result = await _authorized();
      if (!_current(generation)) return;
      post = result;
      unavailable = false;
    } catch (e) {
      if (!_current(generation)) return;
      if (_denied(e)) {
        await _markUnavailable();
      } else {
        message = 'Không thể kiểm tra quyền xem ảnh. Kết nối mạng và thử lại.';
      }
    } finally {
      if (_current(generation)) {
        loading = false;
        _emit();
      }
    }
  }

  Future<bool> save() async {
    if (_disposed || saving || !isSessionActive() || unavailable) return false;
    if (!gallery.supported) {
      message = 'Lưu ảnh chỉ được hỗ trợ trên Android hoặc iOS. Vui lòng dùng ứng dụng di động.';
      _emit();
      return false;
    }
    final generation = ++_generation;
    saving = true;
    loading = false;
    message = null;
    _emit();
    try {
      // Recheck before asking permission: a cached display is never authorization.
      await _authorized();
      if (!_current(generation)) return false;
      final access = await gallery.requestAccess();
      if (!_current(generation)) return false;
      if (access != GalleryAccess.allowed) {
        message = access == GalleryAccess.restricted
            ? 'Quyền thêm ảnh đang bị hạn chế. Kiểm tra Cài đặt > Ảnh hoặc giới hạn của thiết bị.'
            : 'Quyền thêm ảnh bị từ chối. Mở Cài đặt > Ảnh và cho phép thêm ảnh; quyền truy cập giới hạn chỉ cần cho phép thêm.';
        return false;
      }
      // The permission dialog can remain open while the author blocks the viewer.
      final before = await _authorized();
      if (!_current(generation)) return false;
      final bytes = await downloader.download(
        before.mediaUrl,
        maxBytes: maxSaveBytes,
        timeout: const Duration(seconds: 20),
      );
      if (!_current(generation)) return false;
      if (bytes.isEmpty || bytes.length > maxSaveBytes) {
        throw const MediaSaveException(
          'Ảnh vượt quá dung lượng cho phép hoặc bị trống.',
        );
      }
      // Revalidate again after download, before committing bytes to Photos.
      final after = await _authorized();
      if (!_current(generation)) return false;
      if (after.mediaUrl != before.mediaUrl ||
          after.mediaType != before.mediaType) {
        throw const MediaSaveException(
          'Ảnh đã thay đổi. Mở lại ảnh rồi thử lưu lại.',
        );
      }
      await gallery.save(bytes, name: 'loccoc-$postId');
      if (!_current(generation)) return false;
      post = after;
      message = 'Đã lưu ảnh vào thư viện.';
      return true;
    } catch (e) {
      if (!_current(generation)) return false;
      if (_denied(e)) {
        await _markUnavailable();
      } else {
        message = e is MediaSaveException ? e.message : 'Không thể lưu ảnh. Kiểm tra kết nối, quyền Ảnh và dung lượng thiết bị.';
      }
      return false;
    } finally {
      if (!_disposed && generation == _generation) {
        saving = false;
        _emit();
      }
    }
  }

  @override
  void dispose() {
    _disposed = true;
    ++_generation;
    super.dispose();
  }
}
