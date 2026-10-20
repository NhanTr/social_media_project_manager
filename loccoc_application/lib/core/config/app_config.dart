import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;

import 'environment_config.dart';

class AppConfig {
  static const googleServerClientId = String.fromEnvironment(
    'GOOGLE_SERVER_CLIENT_ID',
  );
  static const googleIosClientId = String.fromEnvironment(
    'GOOGLE_IOS_CLIENT_ID',
  );
  static const environment = String.fromEnvironment(
    'APP_ENV',
    defaultValue: 'dev',
  );
  static const _configuredBaseUrl = String.fromEnvironment('API_BASE_URL');
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

  static final EnvironmentConfig parsedEnvironment = EnvironmentConfig.parse(
    environment: environment,
    apiBaseUrl: _configuredBaseUrl,
    devDefaultBaseUrl: defaultBaseUrl,
  );

  static String baseUrl = parsedEnvironment.apiBaseUrl.toString();
}
