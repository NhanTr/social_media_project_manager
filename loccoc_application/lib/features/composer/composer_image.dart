import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:image/image.dart' as img;

enum PhotoFilter { original, mono, warm }

class PhotoException implements Exception {
  const PhotoException(this.message);
  final String message;
  @override
  String toString() => message;
}

class PhotoSettings {
  const PhotoSettings({
    this.square = false,
    this.cropX = .5,
    this.cropY = .5,
    this.filter = PhotoFilter.original,
  });
  final bool square;
  final double cropX, cropY;
  final PhotoFilter filter;
  Map<String, dynamic> toJson() => {
    'square': square,
    'cropX': cropX,
    'cropY': cropY,
    'filter': filter.name,
  };
  factory PhotoSettings.fromJson(Map<String, dynamic> json) => PhotoSettings(
    square: json['square'] == true,
    cropX: (json['cropX'] as num?)?.toDouble() ?? .5,
    cropY: (json['cropY'] as num?)?.toDouble() ?? .5,
    filter: PhotoFilter.values.byName(json['filter'] as String? ?? 'original'),
  );
}

class PreparedPhoto {
  const PreparedPhoto(this.bytes, this.width, this.height);
  final Uint8List bytes;
  final int width, height;
}

abstract interface class PhotoProcessor {
  Future<PreparedPhoto> process(Uint8List source, PhotoSettings settings);
}

class LocalPhotoProcessor implements PhotoProcessor {
  const LocalPhotoProcessor();
  @override
  Future<PreparedPhoto> process(Uint8List source, PhotoSettings settings) =>
      compute(_preparePhoto, (source, settings));
}

PreparedPhoto _preparePhoto((Uint8List, PhotoSettings) input) {
  final (bytes, settings) = input;
  final decoded = img.decodeImage(bytes);
  if (decoded == null) {
    throw const PhotoException(
      'Ảnh không hợp lệ. Vui lòng chọn ảnh JPEG hoặc PNG.',
    );
  }
  var photo = img.bakeOrientation(decoded);
  if (settings.square) {
    final side = math.min(photo.width, photo.height);
    photo = img.copyCrop(
      photo,
      x: ((photo.width - side) * settings.cropX.clamp(0, 1)).round(),
      y: ((photo.height - side) * settings.cropY.clamp(0, 1)).round(),
      width: side,
      height: side,
    );
  }
  if (math.max(photo.width, photo.height) > 2048) {
    photo = img.copyResize(
      photo,
      width: photo.width >= photo.height ? 2048 : null,
      height: photo.height > photo.width ? 2048 : null,
    );
  }
  if (settings.filter == PhotoFilter.mono) photo = img.grayscale(photo);
  if (settings.filter == PhotoFilter.warm) {
    for (final pixel in photo) {
      pixel.r = (pixel.r * 1.08 + 8).clamp(0, 255);
      pixel.g = (pixel.g * 1.02 + 3).clamp(0, 255);
      pixel.b = (pixel.b * .88).clamp(0, 255);
    }
  }
  for (final quality in [90, 80, 70, 60, 45]) {
    final encoded = Uint8List.fromList(img.encodeJpg(photo, quality: quality));
    if (encoded.length < 5 * 1024 * 1024) {
      return PreparedPhoto(encoded, photo.width, photo.height);
    }
  }
  throw const PhotoException(
    'Không thể nén ảnh dưới 5 MiB. Vui lòng chọn ảnh khác.',
  );
}
