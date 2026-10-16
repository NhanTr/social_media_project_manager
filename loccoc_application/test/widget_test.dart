import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:loccoc_application/core/network/api_client.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/auth/auth_controller.dart';
import 'package:loccoc_application/features/auth/screens/login_screen.dart';
import 'package:loccoc_application/features/auth/screens/splash_screen.dart';
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
}
