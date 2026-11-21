import 'dart:async';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:gal/gal.dart';

abstract interface class MediaDownloader {
  Future<Uint8List> download(
    String url, {
    required int maxBytes,
    required Duration timeout,
  });
}

/// Streams bytes with both a total deadline and a hard byte bound, including
/// responses without Content-Length. No account credentials go to media hosts.
class DioMediaDownloader implements MediaDownloader {
  DioMediaDownloader({Dio? dio}) : _dio = dio ?? Dio();
  final Dio _dio;
  @override
  Future<Uint8List> download(
    String url, {
    required int maxBytes,
    required Duration timeout,
  }) async {
    final uri = Uri.tryParse(url);
    if (uri == null ||
        !['https', 'http'].contains(uri.scheme) ||
        !uri.hasAuthority) {
      throw const MediaSaveException('Đường dẫn ảnh không hợp lệ.');
    }
    final cancel = CancelToken();
    final timer = Timer(
      timeout,
      () => cancel.cancel('Download deadline exceeded'),
    );
    try {
      final response = await _dio.get<ResponseBody>(
        url,
        cancelToken: cancel,
        options: Options(
          responseType: ResponseType.stream,
          receiveTimeout: timeout,
          sendTimeout: timeout,
          followRedirects: true,
          maxRedirects: 3,
        ),
      );
      final length = int.tryParse(
        response.headers.value('content-length') ?? '',
      );
      if (length != null && length > maxBytes) {
        cancel.cancel('Image exceeds byte bound');
        throw const MediaSaveException('Ảnh vượt quá dung lượng cho phép.');
      }
      final bytes = BytesBuilder(copy: false);
      await for (final chunk in response.data!.stream) {
        if (bytes.length + chunk.length > maxBytes) {
          cancel.cancel('Image exceeds byte bound');
          throw const MediaSaveException('Ảnh vượt quá dung lượng cho phép.');
        }
        bytes.add(chunk);
      }
      if (bytes.isEmpty) throw const MediaSaveException('Ảnh tải về bị trống.');
      return bytes.takeBytes();
    } on DioException catch (e) {
      if (e.response?.statusCode == 401 ||
          e.response?.statusCode == 403 ||
          e.response?.statusCode == 404) {
        throw const MediaAccessException();
      }
      throw const MediaSaveException(
        'Không thể tải ảnh. Kiểm tra kết nối rồi thử lại.',
      );
    } finally {
      timer.cancel();
    }
  }
}

enum GalleryAccess { allowed, denied, restricted }

abstract interface class GalleryAdapter {
  bool get supported;
  Future<GalleryAccess> requestAccess();
  Future<void> save(Uint8List bytes, {required String name});
}

class GalGalleryAdapter implements GalleryAdapter {
  @override
  bool get supported =>
      !kIsWeb &&
      (defaultTargetPlatform == TargetPlatform.android ||
          defaultTargetPlatform == TargetPlatform.iOS);
  @override
  Future<GalleryAccess> requestAccess() async {
    if (!supported) return GalleryAccess.restricted;
    try {
      // toAlbum:false requests add-only Photos access on iOS. No read permission.
      return await Gal.hasAccess(toAlbum: false) ||
              await Gal.requestAccess(toAlbum: false)
          ? GalleryAccess.allowed
          : GalleryAccess.denied;
    } on GalException catch (_) {
      return GalleryAccess.denied;
    }
  }

  @override
  Future<void> save(Uint8List bytes, {required String name}) async {
    try {
      await Gal.putImageBytes(bytes, name: name);
    } on GalException catch (e) {
      throw MediaSaveException(switch (e.type) {
        GalExceptionType.accessDenied => 'Quyền thêm ảnh bị từ chối hoặc hạn chế. Mở Cài đặt > Ảnh và cho phép thêm ảnh.',
        GalExceptionType.notEnoughSpace =>
          'Thiết bị không đủ dung lượng. Giải phóng bộ nhớ rồi thử lại.',
        GalExceptionType.notSupportedFormat =>
          'Định dạng ảnh không được thư viện hỗ trợ.',
        GalExceptionType.unexpected =>
          'Không thể lưu ảnh. Hãy kiểm tra quyền Ảnh và thử lại.',
      });
    }
  }
}

class MediaSaveException implements Exception {
  const MediaSaveException(this.message);
  final String message;
  @override
  String toString() => message;
}

class MediaAccessException implements Exception {
  const MediaAccessException();
}
