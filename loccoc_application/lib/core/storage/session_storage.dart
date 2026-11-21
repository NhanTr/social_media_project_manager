import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SessionStorage {
  final FlutterSecureStorage _secure = const FlutterSecureStorage();
  static const _keyTokens = 'loccoc_tokens';

  Future<Map<String, dynamic>> _tokens() async {
    final prefs = await SharedPreferences.getInstance();
    // Old plaintext sessions require login again after this security upgrade.
    await prefs.remove(_keyAccessToken);
    await prefs.remove(_keyRefreshToken);
    final value = await _secure.read(key: _keyTokens);
    return value == null ? {} : jsonDecode(value) as Map<String, dynamic>;
  }

  static const String _keyAccessToken = 'loccoc_access_token';
  static const String _keyRefreshToken = 'loccoc_refresh_token';
  static const String _keyUserId = 'loccoc_user_id';
  static const String _keyUsername = 'loccoc_username';
  static const String _keyDisplayName = 'loccoc_display_name';
  static const String _keyRequiresTerms = 'loccoc_requires_terms';

  Future<void> saveSession({
    required String accessToken,
    required String refreshToken,
    required int userId,
    required String username,
    required String displayName,
    required bool requiresTerms,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    await _secure.write(
      key: _keyTokens,
      value: jsonEncode({
        'accessToken': accessToken,
        'refreshToken': refreshToken,
      }),
    );
    await prefs.remove(_keyAccessToken);
    await prefs.remove(_keyRefreshToken);
    await prefs.setInt(_keyUserId, userId);
    await prefs.setString(_keyUsername, username);
    await prefs.setString(_keyDisplayName, displayName);
    await prefs.setBool(_keyRequiresTerms, requiresTerms);
  }

  Future<void> updateTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    await _secure.write(
      key: _keyTokens,
      value: jsonEncode({
        'accessToken': accessToken,
        'refreshToken': refreshToken,
      }),
    );
    await prefs.remove(_keyAccessToken);
    await prefs.remove(_keyRefreshToken);
  }

  Future<void> setTermsAccepted() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyRequiresTerms, false);
  }

  Future<String?> getAccessToken() async {
    return (await _tokens())['accessToken'] as String?;
  }

  Future<String?> getRefreshToken() async {
    return (await _tokens())['refreshToken'] as String?;
  }

  Future<int?> getUserId() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getInt(_keyUserId);
  }

  Future<String?> getUsername() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyUsername);
  }

  Future<String?> getDisplayName() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyDisplayName);
  }

  Future<bool> getRequiresTerms() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_keyRequiresTerms) ?? false;
  }

  Future<void> clear() async {
    await _secure.delete(key: _keyTokens);
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_keyAccessToken);
    await prefs.remove(_keyRefreshToken);
    await prefs.remove(_keyUserId);
    await prefs.remove(_keyUsername);
    await prefs.remove(_keyDisplayName);
    await prefs.remove(_keyRequiresTerms);
  }
}
