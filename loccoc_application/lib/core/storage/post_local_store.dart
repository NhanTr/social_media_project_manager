import 'post_store_preferences.dart'
    if (dart.library.io) 'post_store_files.dart'
    as platform;

/// Durable JSON snapshots. A store can access only its account's directory.
class PostLocalStore {
  final int accountId;
  final String? directory;
  static final Map<String, Future<void>> _pending = {};
  PostLocalStore({required this.accountId, this.directory}) {
    if (accountId <= 0) throw ArgumentError.value(accountId, 'accountId');
  }
  String get _scope => '${directory ?? 'default'}:$accountId';
  void _check(String key) {
    if (!RegExp(r'^[a-zA-Z0-9_-]+$').hasMatch(key)) {
      throw ArgumentError.value(key, 'key');
    }
  }

  Future<T> _run<T>(Future<T> Function() action) {
    final future = (_pending[_scope] ?? Future<void>.value()).then(
      (_) => action(),
    );
    _pending[_scope] = future.then<void>(
      (_) {},
      onError: (Object _, StackTrace _) {},
    );
    return future;
  }

  Future<Map<String, dynamic>?> read(String key) {
    _check(key);
    return _run(() => platform.read(accountId, key, directory));
  }

  Future<void> write(String key, Map<String, dynamic> value) {
    _check(key);
    return _run(() => platform.write(accountId, key, value, directory));
  }

  Future<void> remove(String key) {
    _check(key);
    return _run(() => platform.remove(accountId, key, directory));
  }

  Future<void> clear() => _run(() => platform.clear(accountId, directory));
}
