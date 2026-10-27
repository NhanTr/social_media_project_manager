import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:loccoc_application/core/models/post_models.dart';
import 'package:loccoc_application/core/network/api_client.dart';
import 'package:loccoc_application/core/network/posts_api.dart';
import 'package:loccoc_application/features/composer/composer_controller.dart';
import 'package:loccoc_application/features/composer/composer_image.dart';
import 'package:loccoc_application/features/composer/composer_adapters.dart';

class FakeImagePicker extends ImagePicker {
  PlatformException? pickError;
  LostDataResponse? lostData;
  PlatformException? lostError;
  Future<XFile?> Function()? onPick;
  @override
  Future<XFile?> pickImage({
    required ImageSource source,
    double? maxWidth,
    double? maxHeight,
    int? imageQuality,
    CameraDevice preferredCameraDevice = CameraDevice.rear,
    bool requestFullMetadata = true,
  }) async {
    if (pickError != null) throw pickError!;
    if (onPick != null) return onPick!();
    return null;
  }

  @override
  Future<LostDataResponse> retrieveLostData() async {
    if (lostError != null) throw lostError!;
    return lostData ?? LostDataResponse.empty();
  }
}

class MemoryDrafts implements ComposerDraftStore {
  Map<String, dynamic>? value;
  bool failPrepared = false, failReceipt = false;
  Completer<void>? removePending;
  int removals = 0;
  @override
  Future<Map<String, dynamic>?> read() async => value == null
      ? null
      : jsonDecode(jsonEncode(value)) as Map<String, dynamic>;
  @override
  Future<void> write(Map<String, dynamic> draft) async {
    if (failPrepared && draft['prepared'] != null ||
        failReceipt && draft['receipt'] != null) {
      throw Exception('disk full');
    }
    value = jsonDecode(jsonEncode(draft)) as Map<String, dynamic>;
  }

  @override
  Future<void> remove() async {
    removals++;
    await removePending?.future;
    value = null;
  }
}

class Processor implements PhotoProcessor {
  @override
  Future<PreparedPhoto> process(
    Uint8List source,
    PhotoSettings settings,
  ) async => PreparedPhoto(Uint8List.fromList([4, 5, 6]), 10, 10);
}

class Picker implements ComposerPhotoPicker {
  Uint8List? recovered;
  Object? failure;
  @override
  Future<Uint8List?> pick({required bool camera}) async {
    if (failure != null) throw failure!;
    return Uint8List.fromList([1, 2, 3]);
  }

  @override
  Future<Uint8List?> recover() async => recovered;
}

class Transport implements PhotoUploadTransport {
  UploadAbort? lastAbort;
  Completer<void>? pending;
  int calls = 0;
  @override
  Future<void> put(
    String url,
    Uint8List bytes,
    UploadAbort abort,
    void Function(int, int) progress,
  ) async {
    calls++;
    lastAbort = abort;
    progress(1, 3);
    await pending?.future;
  }
}

class Api extends PostsApi {
  Api(this.store) : super(ApiClient());
  final MemoryDrafts store;
  int uploads = 0, finalized = 0;
  Completer<MediaUploadSession>? createPending;
  Completer<PostModel>? publishPending;
  bool expireFinalize = false;
  bool failPublish = false;
  PostApiException? publishError;
  Completer<UploadedMedia>? finalizePending;
  final requests = <String>[];
  @override
  Future<MediaUploadSession> createUpload(int bytes) async {
    expect(store.value!['prepared'], isNotNull);
    expect(store.value!['clientRequestId'], isNotEmpty);
    uploads++;
    if (createPending != null) return await createPending!.future;
    return MediaUploadSession(uploadId: 1, presignedUrl: 'https://upload');
  }

  @override
  Future<UploadedMedia> finalizeUpload(int id) async {
    finalized++;
    if (expireFinalize) {
      expireFinalize = false;
      throw const PostApiException(
        statusCode: 409,
        code: 'UPLOAD_SESSION_EXPIRED',
        message: 'Expired',
      );
    }
    return finalizePending == null
        ? UploadedMedia.fromJson({
            'mediaUrl': 'https://media',
            'mediaType': 'IMAGE',
            'fileSizeBytes': 3,
          })
        : await finalizePending!.future;
  }

  @override
  Future<PostModel> publish({
    required String clientRequestId,
    required int uploadId,
    required UploadedMedia media,
    required String caption,
    required String filterApplied,
  }) async {
    requests.add(
      '$clientRequestId|$uploadId|$caption|$filterApplied|${media.mediaUrl}',
    );
    if (publishPending != null) return await publishPending!.future;
    if (publishError != null) throw publishError!;
    if (failPublish) throw Exception('response lost');
    return PostModel.fromJson({
      'id': 7,
      'userId': 1,
      'mediaUrl': media.mediaUrl,
      'mediaType': 'IMAGE',
      'status': 'ACTIVE',
      'createdAt': '2026-10-25T00:00:00Z',
    });
  }
}

void main() {
  late MemoryDrafts store;
  late Api api;
  late Transport upload;
  late ComposerController controller;
  setUp(() {
    store = MemoryDrafts();
    api = Api(store);
    upload = Transport();
    controller = ComposerController(
      api: api,
      accountId: 1,
      store: store,
      picker: Picker(),
      processor: Processor(),
      upload: upload,
    );
  });
  test('restores original bytes and edited caption durably', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    await controller.setCaption('hello');
    final restored = ComposerController(
      api: api,
      accountId: 1,
      store: store,
      picker: Picker(),
      processor: Processor(),
      upload: upload,
    );
    await restored.initialize();
    expect(restored.source, [1, 2, 3]);
    expect(restored.caption, 'hello');
    expect(restored.clientRequestId, controller.clientRequestId);
  });
  test('prepared bytes precede network and ambiguous retry replays frozen request after restart', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    await controller.setCaption('hello');
    api.failPublish = true;
    expect(await controller.publish(), isNull);
    expect(controller.canEdit, false);
    expect(await controller.setCaption('changed'), false);
    final restored = ComposerController(
      api: api,
      accountId: 1,
      store: store,
      picker: Picker(),
      processor: Processor(),
      upload: upload,
    );
    await restored.initialize();
    api.failPublish = false;
    expect((await restored.publish())!.id, 7);
    expect(api.requests[0], api.requests[1]);
    expect(api.uploads, 1);
    expect(upload.calls, 1);
    expect(api.finalized, 1);
    expect(store.value!['receipt'], isNotNull);
    await restored.acknowledge();
    expect(store.value, isNull);
  });
  test('cancel during upload prevents finalize and post creation', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    upload.pending = Completer();
    final result = controller.publish();
    while (upload.calls == 0) {
      await Future<void>.delayed(Duration.zero);
    }
    await controller.cancel();
    upload.pending!.complete();
    await result;
    expect(api.finalized, 0);
    expect(api.requests, isEmpty);
    expect(store.value, isNotNull);
  });
  test(
    'cancel aborts even if its durable snapshot fails and reports the failure',
    () async {
      await controller.initialize();
      await controller.pick(camera: false);
      upload.pending = Completer();
      final result = controller.publish();
      while (upload.calls == 0) {
        await Future<void>.delayed(Duration.zero);
      }
      store.failPrepared = true;
      try {
        await expectLater(controller.cancel(), completes);
        expect(upload.lastAbort!.cancelled, true);
        expect(controller.error, contains('Không lưu'));
      } finally {
        upload.pending!.complete();
        await result;
      }
      expect(api.finalized, 0);
      expect(api.requests, isEmpty);
    },
  );
  test('cancel during finalize never proceeds to post creation', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    api.finalizePending = Completer();
    final result = controller.publish();
    while (api.finalized == 0) {
      await Future<void>.delayed(Duration.zero);
    }
    await controller.cancel();
    api.finalizePending!.complete(
      UploadedMedia.fromJson({
        'mediaUrl': 'url',
        'mediaType': 'IMAGE',
        'fileSizeBytes': 3,
      }),
    );
    await result;
    expect(api.requests, isEmpty);
  });
  test('expired finalize session restarts upload safely', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    api.expireFinalize = true;
    expect(await controller.publish(), isNull);
    expect((await controller.publish())!.id, 7);
    expect(api.uploads, 2);
    expect(upload.calls, 2);
  });
  test('cancel while creating upload prevents PUT and publication', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    api.createPending = Completer();
    final result = controller.publish();
    while (api.uploads == 0) {
      await Future<void>.delayed(Duration.zero);
    }
    await controller.cancel();
    api.createPending!.complete(
      const MediaUploadSession(uploadId: 1, presignedUrl: 'url'),
    );
    await result;
    expect(upload.calls, 0);
    expect(api.requests, isEmpty);
  });
  test('cancel after create sent retains immutable reconciliation even if response succeeds', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    api.publishPending = Completer();
    final result = controller.publish();
    while (api.requests.isEmpty) {
      await Future<void>.delayed(Duration.zero);
    }
    await controller.cancel();
    expect(controller.uncertain, true);
    expect(await controller.discard(), false);
    expect(await controller.setCaption('new'), false);
    api.publishPending!.complete(
      PostModel.fromJson({
        'id': 7,
        'userId': 1,
        'mediaUrl': 'url',
        'createdAt': '2026-10-25T00:00:00Z',
      }),
    );
    expect(await result, isNull);
    expect((await controller.publish())!.id, 7);
    expect(api.requests.length, 1);
  });
  test('account mismatch never restores another account draft', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    final other = ComposerController(
      api: api,
      accountId: 2,
      store: store,
      picker: Picker(),
      processor: Processor(),
      upload: upload,
    );
    await other.initialize();
    expect(other.source, isNull);
  });
  test('failed durable prepared write stops before any network', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    store.failPrepared = true;
    expect(await controller.publish(), isNull);
    expect(api.uploads, 0);
    expect(api.requests, isEmpty);
    expect(controller.error, contains('bản nháp'));
  });
  test(
    'receipt write failure cannot return success until it is durable',
    () async {
      await controller.initialize();
      await controller.pick(camera: false);
      store.failReceipt = true;
      expect(await controller.publish(), isNull);
      expect(store.value!['receipt'], isNull);
      store.failReceipt = false;
      expect((await controller.publish())!.id, 7);
      expect(store.value!['receipt'], isNotNull);
      expect(api.requests.length, 1);
    },
  );
  test(
    'lost picker recovery replaces editable draft and is saved immediately',
    () async {
      await controller.initialize();
      await controller.pick(camera: false);
      final picker = Picker()..recovered = Uint8List.fromList([8, 9]);
      final restored = ComposerController(
        api: api,
        accountId: 1,
        store: store,
        picker: picker,
        processor: Processor(),
        upload: upload,
      );
      await restored.initialize();
      expect(restored.source, [8, 9]);
      expect(base64Decode(store.value!['source'] as String), [8, 9]);
    },
  );
  test('lost picker data never changes an ambiguous publication', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    api.failPublish = true;
    await controller.publish();
    final id = controller.clientRequestId;
    final picker = Picker()..recovered = Uint8List.fromList([8, 9]);
    final restored = ComposerController(
      api: api,
      accountId: 1,
      store: store,
      picker: picker,
      processor: Processor(),
      upload: upload,
    );
    await restored.initialize();
    expect(restored.source, [1, 2, 3]);
    expect(restored.clientRequestId, id);
    expect(restored.canEdit, false);
  });
  test('initial definitive validation rejection releases editing and next edit uses a new key', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    final firstKey = controller.clientRequestId;
    api.publishError = const PostApiException(
      statusCode: 400,
      code: 'VALIDATION_FAILED',
      message: 'Invalid caption',
    );
    expect(await controller.publish(), isNull);
    expect(controller.canEdit, true);
    expect(controller.uncertain, false);
    expect(store.value!['publishSent'], false);
    expect(await controller.setCaption('fixed'), true);
    expect(controller.clientRequestId, isNot(firstKey));
    api.publishError = null;
    expect((await controller.publish())!.id, 7);
  });
  test('validation error during ambiguous reconciliation cannot unlock original payload', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    api.failPublish = true;
    await controller.publish();
    final key = controller.clientRequestId;
    api.failPublish = false;
    api.publishError = const PostApiException(
      statusCode: 400,
      code: 'VALIDATION_FAILED',
      message: 'Invalid caption',
    );
    expect(await controller.publish(), isNull);
    expect(controller.canEdit, false);
    expect(controller.uncertain, true);
    expect(controller.clientRequestId, key);
    expect(api.requests[0], api.requests[1]);
  });
  test('caption validation matches backend UTF-16 length for emoji', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    await controller.setCaption('😀' * 1001);
    expect(await controller.publish(), isNull);
    expect(api.uploads, 0);
    expect(controller.error, contains('2000'));
  });
  test('caption limit prevents preparation and any request', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    await controller.setCaption('a' * 2001);
    expect(await controller.publish(), isNull);
    expect(api.uploads, 0);
    expect(controller.error, contains('2000'));
  });
  test('NativeComposerPhotoPicker maps camera and gallery permission denial and Android lost recovery errors to guidance', () async {
    final fake = FakeImagePicker()
      ..pickError = PlatformException(
        code: 'camera_access_denied',
        message: 'The user did not allow camera access.',
      );
    SharedPreferences.setMockInitialValues({});
    final nativePicker = NativeComposerPhotoPicker(accountId: 1, picker: fake);
    await expectLater(
      nativePicker.pick(camera: true),
      throwsA(
        isA<PhotoException>().having(
          (e) => e.message,
          'guidance',
          contains('Mở Cài đặt ứng dụng'),
        ),
      ),
    );
    fake.pickError = PlatformException(
      code: 'photo_access_restricted',
      message: 'Restricted',
    );
    await expectLater(
      nativePicker.pick(camera: false),
      throwsA(
        isA<PhotoException>().having(
          (e) => e.message,
          'guidance',
          contains('Mở Cài đặt ứng dụng'),
        ),
      ),
    );
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    try {
      await (await SharedPreferences.getInstance()).setString(
        NativeComposerPhotoPicker.recoveryOwnerKey,
        '1:pending',
      );
      fake.lostData = LostDataResponse(
        exception: PlatformException(
          code: 'camera_access_denied',
          message: 'Denied',
        ),
      );
      await controller.initialize();
      await controller.pick(camera: false);
      await controller.setCaption('giữ nháp');
      final restored = ComposerController(
        api: api,
        accountId: 1,
        store: store,
        picker: nativePicker,
        processor: Processor(),
        upload: upload,
      );
      await restored.initialize();
      expect(restored.source, [1, 2, 3]);
      expect(restored.caption, 'giữ nháp');
      expect(restored.error, contains('Mở Cài đặt ứng dụng'));
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
  test('Android global lost photo without an owner cannot enter another account draft', () async {
    SharedPreferences.setMockInitialValues({});
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    try {
      final native = NativeComposerPhotoPicker(
        accountId: 2,
        picker: FakeImagePicker()
          ..lostData = LostDataResponse(
            files: [
              XFile.fromData(Uint8List.fromList([8, 9]), name: 'private.jpg'),
            ],
          ),
      );
      final nextAccount = ComposerController(
        api: api,
        accountId: 2,
        store: MemoryDrafts(),
        picker: native,
        processor: Processor(),
        upload: upload,
      );
      await nextAccount.initialize();
      expect(nextAccount.source, isNull);
      nextAccount.dispose();
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });

  test(
    'Android lost photo is restored only for its durable picker owner',
    () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      try {
        for (final accountId in [1, 2]) {
          SharedPreferences.setMockInitialValues({
            NativeComposerPhotoPicker.recoveryOwnerKey: '1:previous-process',
          });
          final native = NativeComposerPhotoPicker(
            accountId: accountId,
            picker: FakeImagePicker()
              ..lostData = LostDataResponse(
                files: [
                  XFile.fromData(
                    Uint8List.fromList([8, 9]),
                    name: 'private.jpg',
                  ),
                ],
              ),
          );
          expect(await native.recover(), accountId == 1 ? [8, 9] : isNull);
        }
      } finally {
        debugDefaultTargetPlatformOverride = null;
      }
    },
  );

  test('Android picker persists ownership before native launch and clears a cancelled selection', () async {
    SharedPreferences.setMockInitialValues({});
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    try {
      final preferences = await SharedPreferences.getInstance();
      final fake = FakeImagePicker()
        ..onPick = () async {
          expect(
            preferences.getString(NativeComposerPhotoPicker.recoveryOwnerKey),
            startsWith('1:'),
          );
          return null;
        };
      await NativeComposerPhotoPicker(
        accountId: 1,
        picker: fake,
      ).pick(camera: false);
      expect(
        preferences.getString(NativeComposerPhotoPicker.recoveryOwnerKey),
        isNull,
      );
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });

  test('expired upload session during publish reconciliation unlocks session and reuploads prepared photo', () async {
    await controller.initialize();
    await controller.pick(camera: false);
    await controller.setCaption('hello');
    final key = controller.clientRequestId;
    api.failPublish = true;
    expect(await controller.publish(), isNull);
    expect(controller.uncertain, isTrue);
    api.failPublish = false;
    api.publishError = const PostApiException(
      statusCode: 409,
      code: 'UPLOAD_NOT_FINALIZED',
      message: 'Finalize this upload before publishing.',
    );
    expect(await controller.publish(), isNull);
    expect(controller.uncertain, isFalse);
    expect(controller.canEdit, isTrue);
    api.publishError = null;
    expect((await controller.publish())!.id, 7);
    expect(controller.clientRequestId, key);
    expect(api.uploads, 2);
    expect(upload.calls, 2);
  });
}
