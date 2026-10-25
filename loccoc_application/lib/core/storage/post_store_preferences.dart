import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

String _prefix(int id) => 'loccoc-posts-$id-';
Future<Map<String, dynamic>?> read(
  int accountId,
  String key,
  String? root,
) async {
  final prefs = await SharedPreferences.getInstance();
  final json = prefs.getString('${_prefix(accountId)}$key');
  if (json == null) return null;
  try {
    return jsonDecode(json) as Map<String, dynamic>;
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
  final prefs = await SharedPreferences.getInstance();
  if (!await prefs.setString('${_prefix(accountId)}$key', jsonEncode(value))) {
    throw StateError('Không thể lưu dữ liệu.');
  }
}

Future<void> remove(int accountId, String key, String? root) async {
  await (await SharedPreferences.getInstance()).remove(
    '${_prefix(accountId)}$key',
  );
}

Future<void> clear(int accountId, String? root) async {
  final prefs = await SharedPreferences.getInstance();
  for (final key in prefs.getKeys().where(
    (k) => k.startsWith(_prefix(accountId)),
  )) {
    await prefs.remove(key);
  }
}
