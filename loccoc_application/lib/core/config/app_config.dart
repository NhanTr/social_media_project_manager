import 'dart:io' show Platform;
import 'package:flutter/foundation.dart' show kIsWeb;

class AppConfig {
  static const String appName = 'LocCoc';
  static const String appVersion = '0.1.0-beta.1';

  /// Determines default API gateway base URL depending on runtime platform:
  /// - Android emulator uses 10.0.2.2:8080 to access host localhost.
  /// - iOS simulator, macOS, and Web use localhost:8080.
  static String get defaultBaseUrl {
    if (kIsWeb) return 'http://localhost:8080';
    try {
      if (Platform.isAndroid) {
        return 'http://10.0.2.2:8080';
      }
    } catch (_) {}
    return 'http://localhost:8080';
  }

  static String baseUrl = defaultBaseUrl;
}
