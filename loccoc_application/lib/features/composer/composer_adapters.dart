import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../core/storage/post_local_store.dart';
import 'composer_image.dart';

abstract interface class ComposerDraftStore {
  Future<Map<String, dynamic>?> read();
  Future<void> write(Map<String, dynamic> draft);
  Future<void> remove();
}

class LocalComposerDraftStore implements ComposerDraftStore {
  LocalComposerDraftStore(int accountId, {PostLocalStore? store})
    : _store = store ?? PostLocalStore(accountId: accountId);
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
  NativeComposerPhotoPicker({required this.accountId, ImagePicker? picker})
    : _picker = picker ?? ImagePicker();
  final int accountId;
  final ImagePicker _picker;
  static const recoveryOwnerKey = 'loccoc_native_picker_owner_v1';
  bool get _android =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;
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
    SharedPreferences? preferences;
    String? owner;
    if (_android) {
      preferences = await SharedPreferences.getInstance();
      owner = '$accountId:${DateTime.now().microsecondsSinceEpoch}';
      if (!await preferences.setString(recoveryOwnerKey, owner)) {
        throw const PhotoException(
          'Không lưu được chủ sở hữu bản nháp. Vui lòng thử lại.',
        );
      }
    }
    try {
      final file = await _picker.pickImage(
        source: camera ? ImageSource.camera : ImageSource.gallery,
        requestFullMetadata: false,
      );
      return file == null ? null : await _read(file);
    } on PlatformException catch (e) {
      throw PhotoException(_permissionMessage(e));
    } finally {
      if (owner != null && preferences!.getString(recoveryOwnerKey) == owner) {
        await preferences.remove(recoveryOwnerKey);
      }
    }
  }

  @override
  Future<Uint8List?> recover() async {
    if (!_android) return null;
    final preferences = await SharedPreferences.getInstance();
    final owner = preferences.getString(recoveryOwnerKey);
    final owned = owner?.split(':').first == '$accountId';
    try {
      final data = await _picker.retrieveLostData();
      // Android's lost-data cache is app-global. Drain an unowned result without
      // exposing or persisting its bytes under the currently signed-in account.
      if (!owned) return null;
      if (data.exception != null) {
        throw PhotoException(_permissionMessage(data.exception!));
      }
      return data.files?.isNotEmpty == true
          ? await _read(data.files!.first)
          : null;
    } on PlatformException catch (e) {
      if (!owned) return null;
      throw PhotoException(_permissionMessage(e));
    } finally {
      if (owned && preferences.getString(recoveryOwnerKey) == owner) {
        await preferences.remove(recoveryOwnerKey);
      }
    }
  }

  String _permissionMessage(PlatformException error) {
    final code = error.code.toLowerCase();
    if (code.contains('access') || code.contains('permission')) {
      return 'Quyền máy ảnh hoặc thư viện ảnh đã bị từ chối. Mở Cài đặt ứng dụng, cấp quyền rồi thử lại.';
    }
    if (code == 'no_available_camera') {
      return 'Thiết bị không có máy ảnh khả dụng. Hãy chọn ảnh từ thư viện.';
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
