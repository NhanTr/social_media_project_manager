import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/composer/composer_adapters.dart';

void main() {
  test('Dio PUT sends JPEG bytes with real transmitted progress', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final received = Completer<List<int>>();
    server.listen((request) async {
      expect(request.method, 'PUT');
      expect(request.headers.contentType!.mimeType, 'image/jpeg');
      final bytes = await request.fold<List<int>>(
        [],
        (all, chunk) => all..addAll(chunk),
      );
      received.complete(bytes);
      request.response.statusCode = 200;
      await request.response.close();
    });
    try {
      final bytes = Uint8List.fromList(
        List<int>.generate(200000, (i) => i % 256),
      );
      final progress = <int>[];
      await DioPhotoUploadTransport().put(
        'http://127.0.0.1:${server.port}/image',
        bytes,
        UploadAbort(),
        (sent, total) {
          expect(total, bytes.length);
          progress.add(sent);
        },
      );
      expect(await received.future, bytes);
      expect(progress, isNotEmpty);
      expect(progress.last, bytes.length);
    } finally {
      await server.close(force: true);
    }
  });
  test('abort cancels the active PUT while awaiting its response', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final requestSeen = Completer<void>();
    server.listen((request) async {
      await request.drain<void>();
      requestSeen.complete();
    });
    try {
      final abort = UploadAbort();
      final result = DioPhotoUploadTransport().put(
        'http://127.0.0.1:${server.port}/image',
        Uint8List(1000),
        abort,
        (_, _) {},
      );
      final expectation = expectLater(
        result,
        throwsA(
          isA<DioException>().having(
            (e) => e.type,
            'cancel type',
            DioExceptionType.cancel,
          ),
        ),
      );
      await requestSeen.future;
      abort.cancel();
      await expectation;
      expect(abort.cancelled, true);
    } finally {
      await server.close(force: true);
    }
  });
}
