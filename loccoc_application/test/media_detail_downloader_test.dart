import 'dart:async';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/feed/media_adapters.dart';

class StreamAdapter implements HttpClientAdapter {
  StreamAdapter(this.body);
  final ResponseBody body;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async => body;
  @override
  void close({bool force = false}) {}
}

void main() {
  test(
    'stream download rejects oversize without trusting content-length',
    () async {
      final dio = Dio()
        ..httpClientAdapter = StreamAdapter(
          ResponseBody(Stream.fromIterable([Uint8List(3), Uint8List(3)]), 200),
        );
      final downloader = DioMediaDownloader(dio: dio);
      await expectLater(
        downloader.download(
          'https://media.test/photo.jpg',
          maxBytes: 5,
          timeout: const Duration(seconds: 1),
        ),
        throwsA(isA<MediaSaveException>()),
      );
    },
  );
  test('download deadline aborts a stalled response stream', () async {
    final stream = StreamController<Uint8List>();
    final dio = Dio()
      ..httpClientAdapter = StreamAdapter(ResponseBody(stream.stream, 200));
    final downloader = DioMediaDownloader(dio: dio);
    await expectLater(
      downloader
          .download(
            'https://media.test/photo.jpg',
            maxBytes: 5,
            timeout: const Duration(milliseconds: 30),
          )
          .timeout(const Duration(seconds: 1)),
      throwsA(isA<MediaSaveException>()),
    );
    await stream.close();
  });
  test(
    'unauthorized media download reports unavailable rather than saves',
    () async {
      final dio = Dio()
        ..httpClientAdapter = StreamAdapter(ResponseBody.fromString('', 403));
      final downloader = DioMediaDownloader(dio: dio);
      await expectLater(
        downloader.download(
          'https://media.test/photo.jpg',
          maxBytes: 5,
          timeout: const Duration(seconds: 1),
        ),
        throwsA(isA<MediaAccessException>()),
      );
    },
  );
  test(
    'download returns exact bytes at boundary and rejects nonnetwork URLs',
    () async {
      final dio = Dio()
        ..httpClientAdapter = StreamAdapter(
          ResponseBody(Stream.value(Uint8List.fromList([1, 2, 3])), 200),
        );
      final downloader = DioMediaDownloader(dio: dio);
      expect(
        await downloader.download(
          'https://media.test/photo.jpg',
          maxBytes: 3,
          timeout: const Duration(seconds: 1),
        ),
        [1, 2, 3],
      );
      await expectLater(
        downloader.download(
          'file:///tmp/photo.jpg',
          maxBytes: 3,
          timeout: const Duration(seconds: 1),
        ),
        throwsA(isA<MediaSaveException>()),
      );
    },
  );
}
