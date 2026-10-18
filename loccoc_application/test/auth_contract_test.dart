import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:loccoc_application/core/models/user_models.dart';
import 'package:loccoc_application/core/network/api_client.dart';
import 'package:loccoc_application/core/storage/session_storage.dart';
import 'package:loccoc_application/features/auth/auth_controller.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
  });
  test('OTP uses backend devOtp response and otp request contract', () async {
    final api = ApiClient(
      client: MockClient((request) async {
        if (request.url.path.endsWith('send-otp')) {
          return http.Response('{"devOtp":"654321"}', 200);
        }
        expect(jsonDecode(request.body)['otp'], '654321');
        return http.Response(
          '{"accessToken":"a","refreshToken":"r","requiresTermsAcceptance":true,"user":{"id":1,"username":"tester"}}',
          200,
        );
      }),
    );
    final auth = AuthController(api: api);
    expect(await auth.requestPhoneOtp('+84900000001'), isTrue);
    expect(auth.lastOtpMock, '654321');
    expect(await auth.verifyPhoneOtp('+84900000001', '654321'), isTrue);
    expect(auth.status, AuthStatus.requiresTerms);
  });
  test('search preserves relationship flags from backend', () {
    final result = SearchResultModel.fromJson({
      'user': {'id': 2, 'username': 'friend'},
      'friend': true,
      'blocked': true,
    });
    expect(result.areFriends, isTrue);
    expect(result.isBlocked, isTrue);
  });

  test('explicit refresh is single-flight and persists rotated tokens', () async {
    final storage = SessionStorage();
    await storage.saveSession(
      accessToken: 'old-access',
      refreshToken: 'old-refresh',
      userId: 1,
      username: 'tester',
      displayName: 'Tester',
      requiresTerms: true,
    );
    var refreshCalls = 0;
    final api = ApiClient(
      storage: storage,
      client: MockClient((request) async {
        refreshCalls++;
        await Future<void>.delayed(const Duration(milliseconds: 10));
        expect(request.url.path, '/api/v1/auth/refresh');
        return http.Response(
          '{"accessToken":"new-access","refreshToken":"new-refresh"}',
          200,
        );
      }),
    );

    final results = await Future.wait([
      api.refreshAccessToken(),
      api.refreshAccessToken(),
    ]);

    expect(results, [isTrue, isTrue]);
    expect(refreshCalls, 1);
    expect(await storage.getAccessToken(), 'new-access');
    expect(await storage.getRefreshToken(), 'new-refresh');
  });

  test(
    'terms acceptance refreshes before authenticating and remains retryable on failure',
    () async {
      final storage = SessionStorage();
      await storage.saveSession(
        accessToken: 'old-access',
        refreshToken: 'old-refresh',
        userId: 1,
        username: 'tester',
        displayName: 'Tester',
        requiresTerms: true,
      );
      var refreshAttempts = 0;
      final requests = <String>[];
      final api = ApiClient(
        storage: storage,
        client: MockClient((request) async {
          requests.add(request.url.path);
          switch (request.url.path) {
            case '/actuator/health':
              return http.Response('{}', 200);
            case '/api/v1/terms/accept':
              return http.Response('{}', 200);
            case '/api/v1/auth/refresh':
              refreshAttempts++;
              if (refreshAttempts == 1) {
                return http.Response('{"detail":"temporary failure"}', 503);
              }
              return http.Response(
                '{"accessToken":"new-access","refreshToken":"new-refresh"}',
                200,
              );
            case '/api/v1/users/me':
              return http.Response(
                '{"id":1,"username":"tester","displayName":"Tester"}',
                200,
              );
            default:
              return http.Response('{}', 404);
          }
        }),
      );
      final auth = AuthController(api: api, storage: storage);
      await auth.bootstrap();
      expect(auth.status, AuthStatus.requiresTerms);

      requests.clear();
      expect(await auth.acceptTerms(7), isFalse);
      expect(requests, [
        '/api/v1/terms/accept',
        '/api/v1/auth/refresh',
      ]);
      expect(auth.status, AuthStatus.requiresTerms);
      expect(await storage.getRequiresTerms(), isTrue);
      expect(await storage.getAccessToken(), 'old-access');

      requests.clear();
      expect(await auth.acceptTerms(7), isTrue);
      expect(requests, [
        '/api/v1/terms/accept',
        '/api/v1/auth/refresh',
        '/api/v1/users/me',
      ]);
      expect(auth.status, AuthStatus.authenticated);
      expect(await storage.getRequiresTerms(), isFalse);
      expect(await storage.getAccessToken(), 'new-access');
    },
  );
}
