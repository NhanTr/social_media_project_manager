import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

Future<Directory> _directory(int accountId, String? root) async {
  final base = root ?? (await getApplicationSupportDirectory()).path;
  return Directory('$base/loccoc-posts/account-$accountId');
}

Future<Map<String, dynamic>?> read(
  int accountId,
  String key,
  String? root,
) async {
  final file = File('${(await _directory(accountId, root)).path}/$key.json');
  if (!await file.exists()) return null;
  try {
    return jsonDecode(await file.readAsString()) as Map<String, dynamic>;
  } on FormatException {
    return null;
  } on TypeError {
    return null;
  }
}

Future<void> write(
  int accountId,
  String key,
  Map<String, dynamic> value,
  String? root,
) async {
  final dir = await _directory(accountId, root);
  await dir.create(recursive: true);
  final file = File('${dir.path}/$key.json');
  final pending = File('${file.path}.pending');
  await pending.writeAsString(jsonEncode(value), flush: true);
  await pending.rename(file.path);
}

Future<void> remove(int accountId, String key, String? root) async {
  final dir = await _directory(accountId, root);
  for (final suffix in ['.json', '.json.pending']) {
    final file = File('${dir.path}/$key$suffix');
    if (await file.exists()) await file.delete();
  }
}

Future<void> clear(int accountId, String? root) async {
  final dir = await _directory(accountId, root);
  if (await dir.exists()) await dir.delete(recursive: true);
}
