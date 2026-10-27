import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/core/storage/post_local_store.dart';

void main() {
  late Directory dir;
  setUp(() async {
    dir = await Directory.systemTemp.createTemp('loccoc-post-store');
  });
  tearDown(() async {
    await dir.delete(recursive: true);
  });
  test(
    'durable snapshot survives new store and is isolated by account',
    () async {
      final a = PostLocalStore(accountId: 1, directory: dir.path);
      await a.write('draft', {'caption': 'saved', 'bytes': 'AQID'});
      expect(
        await PostLocalStore(accountId: 1, directory: dir.path).read('draft'),
        {'caption': 'saved', 'bytes': 'AQID'},
      );
      expect(
        await PostLocalStore(accountId: 2, directory: dir.path).read('draft'),
        isNull,
      );
      await PostLocalStore(accountId: 2, directory: dir.path).clear();
      expect((await a.read('draft'))!['caption'], 'saved');
      await a.remove('draft');
      expect(await a.read('draft'), isNull);
    },
  );
  test(
    'overlapping writes complete in order and clear only own account',
    () async {
      final a = PostLocalStore(accountId: 1, directory: dir.path);
      final b = PostLocalStore(accountId: 1, directory: dir.path);
      await Future.wait([
        a.write('draft', {'n': 1}),
        b.write('draft', {'n': 2}),
      ]);
      expect(await a.read('draft'), {'n': 2});
      await a.clear();
      expect(await b.read('draft'), isNull);
    },
  );
}
