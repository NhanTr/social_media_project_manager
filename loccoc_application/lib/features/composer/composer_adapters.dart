import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/storage/post_local_store.dart';
import 'composer_image.dart';

abstract interface class ComposerDraftStore {
  Future<Map<String, dynamic>?> read();
  Future<void> write(Map<String, dynamic> draft);
  Future<void> remove();
}

class LocalComposerDraftStore implements ComposerDraftStore {
  LocalComposerDraftStore(int accountId)
    : _store = PostLocalStore(accountId: accountId);
  final PostLocalStore _store;
  @override
  Future<Map<String, dynamic>?> read() => _store.read('composer');
  @override
  Future<void> write(Map<String, dynamic> draft) =>
      _store.write('composer', draft);
  @override
  Future<void> remove() => _store.remove('composer');
}

abstract interface class ComposerPhotoPicker {
  Future<Uint8List?> pick({required bool camera});
  Future<Uint8List?> recover();
}

class NativeComposerPhotoPicker implements ComposerPhotoPicker {
  NativeComposerPhotoPicker({ImagePicker? picker})
    : _picker = picker ?? ImagePicker();
  final ImagePicker _picker;
  Future<Uint8List> _read(XFile file) async {
    if (await file.length() > 30 * 1024 * 1024) {
      throw const PhotoException('Ảnh gốc quá lớn (tối đa 30 MiB).');
    }
    return file.readAsBytes();
  }

  @override
  Future<Uint8List?> pick({required bool camera}) async {
    if (camera && kIsWeb) {
      throw const PhotoException(
        'Chụp ảnh chưa được hỗ trợ trên web. Hãy chọn ảnh từ thư viện.',
      );
    }
    try {
      final file = await _picker.pickImage(
        source: camera ? ImageSource.camera : ImageSource.gallery,
        requestFullMetadata: false,
      );
      return file == null ? null : await _read(file);
    } on PlatformException catch (e) {
      throw PhotoException(_permissionMessage(e));
    }
  }

  @override
  Future<Uint8List?> recover() async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return null;
    final data = await _picker.retrieveLostData();
    if (data.exception != null) {
      throw PhotoException(_permissionMessage(data.exception!));
    }
    return data.files?.isNotEmpty == true ? _read(data.files!.first) : null;
  }

  String _permissionMessage(PlatformException error) {
    if (error.code.toLowerCase().contains('access') ||
        error.code.toLowerCase().contains('permission')) {
      return 'Quyền máy ảnh hoặc thư viện ảnh đã bị từ chối. Mở Cài đặt ứng dụng, cấp quyền rồi thử lại.';
    }
    return 'Không mở được ảnh. ${error.message ?? 'Vui lòng thử lại.'}';
  }
}

class UploadAbort {
  final CancelToken _token = CancelToken();
  bool get cancelled => _token.isCancelled;
  void cancel() {
    if (!cancelled) _token.cancel('User cancelled');
  }
}

abstract interface class PhotoUploadTransport {
  Future<void> put(
    String url,
    Uint8List bytes,
    UploadAbort abort,
    void Function(int sent, int total) progress,
  );
}

class DioPhotoUploadTransport implements PhotoUploadTransport {
  DioPhotoUploadTransport({Dio? dio})
    : _dio =
          dio ??
          Dio(
            BaseOptions(
              connectTimeout: const Duration(seconds: 20),
              sendTimeout: const Duration(minutes: 2),
              receiveTimeout: const Duration(seconds: 30),
            ),
          );
  final Dio _dio;
  @override
  Future<void> put(
    String url,
    Uint8List bytes,
    UploadAbort abort,
    void Function(int, int) progress,
  ) async {
    await _dio.put<void>(
      url,
      data: Stream<Uint8List>.value(bytes),
      options: Options(
        headers: {'Content-Type': 'image/jpeg', 'Content-Length': bytes.length},
        responseType: ResponseType.plain,
      ),
      cancelToken: abort._token,
      onSendProgress: progress,
    );
  }
}
