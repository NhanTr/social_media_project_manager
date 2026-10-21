import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:loccoc_application/core/network/api_client.dart';
import 'package:loccoc_application/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/auth/auth_controller.dart';
import 'package:loccoc_application/features/auth/screens/login_screen.dart';
import 'package:loccoc_application/features/auth/screens/splash_screen.dart';
import 'package:loccoc_application/features/auth/screens/terms_screen.dart';
import 'package:loccoc_application/main.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
  });
  testWidgets('Splash screen displays LocCoc branding', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: SplashScreen()));

    expect(find.text('LocCoc'), findsOneWidget);
    expect(find.text('Chia sẻ khoảnh khắc cùng bạn bè'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('Login screen renders phone input and action buttons', (
    WidgetTester tester,
  ) async {
    final authController = AuthController();

    await tester.pumpWidget(
      MaterialApp(home: LoginScreen(authController: authController)),
    );

    expect(find.text('Chào mừng đến LocCoc'), findsOneWidget);
    expect(find.text('Số điện thoại'), findsOneWidget);
    expect(find.text('Gửi mã OTP'), findsOneWidget);
    expect(find.text('Đăng nhập với Google'), findsOneWidget);
  });

  testWidgets('App routing navigates to LoginScreen when unauthenticated', (
    WidgetTester tester,
  ) async {
    final authController = AuthController(
      api: ApiClient(client: MockClient((_) async => http.Response('{}', 200))),
    );
    await tester.runAsync(authController.bootstrap);

    await tester.pumpWidget(LocCocApp(authController: authController));

    await tester.pumpAndSettle();
    expect(find.byType(LoginScreen), findsOneWidget);
  });

  test(
    'expired session clears storage and remains a login transition',
    () async {
      final storage = SessionStorage();
      await storage.saveSession(
        accessToken: 'expired-access',
        refreshToken: 'expired-refresh',
        userId: 1,
        username: 'tester',
        displayName: 'Tester',
        requiresTerms: false,
      );
      final authController = AuthController(
        storage: storage,
        api: ApiClient(
          storage: storage,
          client: MockClient((request) async {
            if (request.url.path == '/actuator/health') {
              return http.Response('{}', 200);
            }
            return http.Response('{"detail":"expired"}', 401);
          }),
        ),
      );

      await authController.bootstrap();

      expect(authController.status, AuthStatus.unauthenticated);
      expect(await storage.getAccessToken(), isNull);
      expect(await storage.getRefreshToken(), isNull);
    },
  );

  testWidgets('failed post-consent refresh keeps the retryable Terms screen', (
    WidgetTester tester,
  ) async {
    final storage = SessionStorage();
    await storage.saveSession(
      accessToken: 'old-access',
      refreshToken: 'old-refresh',
      userId: 1,
      username: 'tester',
      displayName: 'Tester',
      requiresTerms: true,
    );
    final authController = AuthController(
      storage: storage,
      api: ApiClient(
        storage: storage,
        client: MockClient((request) async {
          switch (request.url.path) {
            case '/actuator/health':
              return http.Response('{}', 200);
            case '/api/v1/terms/latest':
              return http.Response(
                '{"id":7,"version":"v2","contentUrl":"https://loccoc.app/terms","publishedAt":"2026-10-19T00:00:00Z"}',
                200,
              );
            case '/api/v1/terms/accept':
              return http.Response('{}', 200);
            case '/api/v1/auth/refresh':
              return http.Response('{"detail":"temporary failure"}', 503);
            default:
              return http.Response('{}', 404);
          }
        }),
      ),
    );
    await authController.bootstrap();

    await tester.pumpWidget(LocCocApp(authController: authController));
    await tester.pumpAndSettle();
    expect(find.byType(TermsScreen), findsOneWidget);

    await tester.tap(find.byType(CheckboxListTile));
    await tester.pump();
    await tester.tap(find.text('Đồng ý & Tiếp tục'));
    await tester.pumpAndSettle();

    expect(authController.status, AuthStatus.requiresTerms);
    expect(find.byType(TermsScreen), findsOneWidget);
    expect(await storage.getRequiresTerms(), isTrue);
  });
}
