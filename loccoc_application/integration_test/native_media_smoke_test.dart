import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as image;
import 'package:integration_test/integration_test.dart';
import 'package:loccoc_application/features/feed/media_adapters.dart';

// Run on a task-owned simulator/emulator with add-only Photos access granted.
// Exercises our production native adapter and its real plugin wiring.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('production gallery adapter saves JPEG to the device library', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: Center(child: Text('LocCoc native media verification')),
        ),
      ),
    );
    final gallery = GalGalleryAdapter();
    expect(gallery.supported, isTrue);
    expect(await gallery.requestAccess(), GalleryAccess.allowed);
    final photo = image.Image(width: 64, height: 64);
    image.fill(photo, color: image.ColorRgb8(23, 54, 93));
    await expectLater(
      gallery.save(
        Uint8List.fromList(image.encodeJpg(photo)),
        name: 'loccoc-s2-qa',
      ),
      completes,
    );
  });
}
