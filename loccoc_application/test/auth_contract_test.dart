import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:loccoc_application/core/models/user_models.dart';
import 'package:loccoc_application/core/network/api_client.dart';
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
}
