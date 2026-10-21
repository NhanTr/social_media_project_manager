import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/core/config/environment_config.dart';

void main() {
  group('EnvironmentConfig.parse', () {
    test('uses the supplied platform default for dev without a URL', () {
      final config = EnvironmentConfig.parse(
        environment: 'dev',
        apiBaseUrl: '',
        devDefaultBaseUrl: 'http://10.0.2.2:8080',
      );

      expect(config.environment, AppEnvironment.dev);
      expect(config.apiBaseUrl.toString(), 'http://10.0.2.2:8080');
    });

    test('rejects staging without an absolute URL', () {
      expect(
        () => EnvironmentConfig.parse(environment: 'staging', apiBaseUrl: ''),
        throwsArgumentError,
      );
    });

    test('rejects production over plain HTTP', () {
      expect(
        () => EnvironmentConfig.parse(
          environment: 'prod',
          apiBaseUrl: 'http://api.example.com',
        ),
        throwsArgumentError,
      );
    });

    test('accepts an absolute HTTPS production URL', () {
      final config = EnvironmentConfig.parse(
        environment: 'prod',
        apiBaseUrl: 'https://api.example.com',
      );

      expect(config.environment, AppEnvironment.prod);
      expect(config.apiBaseUrl.toString(), 'https://api.example.com');
    });

    for (final value in ['', 'qa']) {
      test('rejects unsupported environment "$value"', () {
        expect(
          () => EnvironmentConfig.parse(
            environment: value,
            apiBaseUrl: 'https://api.example.com',
          ),
          throwsArgumentError,
        );
      });
    }
  });
}
