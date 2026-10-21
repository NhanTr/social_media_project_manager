enum AppEnvironment { dev, staging, prod }

class EnvironmentConfig {
  const EnvironmentConfig._({
    required this.environment,
    required this.apiBaseUrl,
  });

  final AppEnvironment environment;
  final Uri apiBaseUrl;

  factory EnvironmentConfig.parse({
    required String environment,
    required String apiBaseUrl,
    String devDefaultBaseUrl = 'http://localhost:8080',
  }) {
    final parsedEnvironment = switch (environment.trim().toLowerCase()) {
      'dev' => AppEnvironment.dev,
      'staging' => AppEnvironment.staging,
      'prod' => AppEnvironment.prod,
      _ => throw ArgumentError.value(
        environment,
        'environment',
        'Expected dev, staging, or prod',
      ),
    };

    final selectedUrl = apiBaseUrl.trim().isEmpty
        ? parsedEnvironment == AppEnvironment.dev
              ? devDefaultBaseUrl
              : throw ArgumentError(
                  'API_BASE_URL is required for ${parsedEnvironment.name}',
                )
        : apiBaseUrl.trim();
    final parsedUrl = Uri.tryParse(selectedUrl);

    if (parsedUrl == null ||
        !parsedUrl.isAbsolute ||
        parsedUrl.host.isEmpty ||
        (parsedUrl.scheme != 'http' && parsedUrl.scheme != 'https')) {
      throw ArgumentError.value(
        selectedUrl,
        'apiBaseUrl',
        'Expected an absolute HTTP(S) URL',
      );
    }
    if (parsedEnvironment == AppEnvironment.prod &&
        parsedUrl.scheme != 'https') {
      throw ArgumentError.value(
        selectedUrl,
        'apiBaseUrl',
        'Production requires HTTPS',
      );
    }

    return EnvironmentConfig._(
      environment: parsedEnvironment,
      apiBaseUrl: parsedUrl,
    );
  }
}
