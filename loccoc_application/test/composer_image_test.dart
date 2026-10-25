import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as im;
import 'package:loccoc_application/features/composer/composer_image.dart';

void main() {
  test(
    'square crop uses adjustable offset and filters actual JPEG pixels',
    () async {
      final source = im.Image(width: 120, height: 60);
      im.fillRect(
        source,
        x1: 0,
        y1: 0,
        x2: 59,
        y2: 59,
        color: im.ColorRgb8(220, 20, 30),
      );
      im.fillRect(
        source,
        x1: 60,
        y1: 0,
        x2: 119,
        y2: 59,
        color: im.ColorRgb8(20, 40, 220),
      );
      final result = await const LocalPhotoProcessor().process(
        Uint8List.fromList(im.encodePng(source)),
        const PhotoSettings(square: true, cropX: 1, filter: PhotoFilter.mono),
      );
      final decoded = im.decodeJpg(result.bytes)!;
      expect(decoded.width, 60);
      expect(decoded.height, 60);
      final pixel = decoded.getPixel(30, 30);
      expect((pixel.r - pixel.b).abs(), lessThan(3));
      expect(pixel.r, lessThan(70));
      expect(result.bytes.length, lessThan(5 * 1024 * 1024));
    },
  );
  test(
    'bakes EXIF orientation and warm filter changes encoded output',
    () async {
      final source = im.Image(width: 20, height: 10);
      im.fill(source, color: im.ColorRgb8(100, 100, 100));
      source.exif.imageIfd.orientation = 6;
      final bytes = Uint8List.fromList(im.encodeJpg(source));
      final result = await const LocalPhotoProcessor().process(
        bytes,
        const PhotoSettings(filter: PhotoFilter.warm),
      );
      expect(result.width, 10);
      expect(result.height, 20);
      final decoded = im.decodeJpg(result.bytes)!;
      final pixel = decoded.getPixel(5, 5);
      expect(pixel.r, greaterThan(pixel.g));
      expect(pixel.g, greaterThan(pixel.b));
      expect(decoded.exif.imageIfd.orientation, anyOf(isNull, 1));
    },
  );
  test('resizes original ratio and rejects corrupt input', () async {
    final result = await const LocalPhotoProcessor().process(
      Uint8List.fromList(im.encodePng(im.Image(width: 2500, height: 500))),
      const PhotoSettings(),
    );
    expect(result.width, 2048);
    expect(result.height, 410);
    await expectLater(
      const LocalPhotoProcessor().process(
        Uint8List.fromList([1, 2, 3]),
        const PhotoSettings(),
      ),
      throwsA(isA<PhotoException>()),
    );
  });
}
