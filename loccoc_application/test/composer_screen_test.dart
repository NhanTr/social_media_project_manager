import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/composer/composer_controller.dart';
import 'package:loccoc_application/features/composer/composer_image.dart';
import 'package:loccoc_application/features/composer/composer_screen.dart';

import 'composer_controller_test.dart' as fixtures;

void main() {
  testWidgets(
    'restores caption and exposes photo, crop, filters, retry and discard controls',
    (tester) async {
      final store = fixtures.MemoryDrafts();
      final api = fixtures.Api(store);
      final controller = ComposerController(
        api: api,
        accountId: 1,
        store: store,
        picker: fixtures.Picker(),
        processor: fixtures.Processor(),
        upload: fixtures.Transport(),
      );
      await controller.initialize();
      await controller.pick(camera: false);
      await controller.setCaption('Bản nháp');
      await tester.pumpWidget(
        MaterialApp(
          home: ComposerScreen(api: api, accountId: 1, controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Thư viện'), findsOneWidget);
      expect(find.text('Vuông'), findsOneWidget);
      expect(find.text('Ấm'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.byType(TextField),
        220,
        scrollable: find.byType(Scrollable).first,
      );
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        'Bản nháp',
      );
      await tester.scrollUntilVisible(
        find.text('Đăng ảnh'),
        180,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Đăng ảnh'), findsOneWidget);
      expect(find.byTooltip('Bỏ bản nháp'), findsOneWidget);
    },
  );
  testWidgets(
    'submission stays locked while acknowledging the durable receipt',
    (tester) async {
      final store = fixtures.MemoryDrafts()..removePending = Completer<void>();
      final api = fixtures.Api(store);
      final controller = ComposerController(
        api: api,
        accountId: 1,
        store: store,
        picker: fixtures.Picker(),
        processor: fixtures.Processor(),
        upload: fixtures.Transport(),
      );
      await controller.initialize();
      await controller.pick(camera: false);
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => ComposerScreen(
                      api: api,
                      accountId: 1,
                      controller: controller,
                    ),
                  ),
                ),
                child: const Text('Open composer'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open composer'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Đăng ảnh'),
        220,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(find.text('Đăng ảnh'));
      await tester.pump();
      await tester.pump();
      expect(store.removals, 1);
      final submit = find.byWidgetPredicate((widget) => widget is FilledButton);
      final enabled = tester.widget<FilledButton>(submit).onPressed != null;
      await tester.tap(submit);
      await tester.pump();
      final removals = store.removals;
      store.removePending!.complete();
      await tester.pumpAndSettle();
      expect(enabled, false);
      expect(removals, 1);
      expect(api.requests.length, 1);
    },
  );
  testWidgets('ambiguous publication locks caption and warns before retry', (
    tester,
  ) async {
    final store = fixtures.MemoryDrafts();
    final api = fixtures.Api(store)..failPublish = true;
    final controller = ComposerController(
      api: api,
      accountId: 1,
      store: store,
      picker: fixtures.Picker(),
      processor: fixtures.Processor(),
      upload: fixtures.Transport(),
    );
    await controller.initialize();
    await controller.pick(camera: false);
    await controller.publish();
    await tester.pumpWidget(
      MaterialApp(
        home: ComposerScreen(api: api, accountId: 1, controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byType(TextField),
      220,
      scrollable: find.byType(Scrollable).first,
    );
    expect(tester.widget<TextField>(find.byType(TextField)).enabled, false);
    await tester.scrollUntilVisible(
      find.text('Thử lại để xác nhận'),
      180,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('Thử lại để xác nhận'), findsOneWidget);
    expect(find.textContaining('nội dung đang được khóa'), findsOneWidget);
  });
  testWidgets(
    'camera and gallery permission denial displays guidance and allows retry after granting',
    (tester) async {
      final store = fixtures.MemoryDrafts();
      final api = fixtures.Api(store);
      final picker = fixtures.Picker()
        ..failure = const PhotoException(
          'Quyền máy ảnh hoặc thư viện ảnh đã bị từ chối. Mở Cài đặt ứng dụng, cấp quyền rồi thử lại.',
        );
      final controller = ComposerController(
        api: api,
        accountId: 1,
        store: store,
        picker: picker,
        processor: fixtures.Processor(),
        upload: fixtures.Transport(),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: ComposerScreen(api: api, accountId: 1, controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Máy ảnh'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.textContaining('Mở Cài đặt ứng dụng, cấp quyền rồi thử lại.'),
        180,
        scrollable: find.byType(Scrollable).first,
      );
      expect(
        find.textContaining('Mở Cài đặt ứng dụng, cấp quyền rồi thử lại.'),
        findsOneWidget,
      );
      picker.failure = null;
      await tester.scrollUntilVisible(
        find.text('Thư viện'),
        -180,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(find.text('Thư viện'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Mở Cài đặt ứng dụng, cấp quyền rồi thử lại.'),
        findsNothing,
      );
      expect(controller.source, [1, 2, 3]);
    },
  );
}
