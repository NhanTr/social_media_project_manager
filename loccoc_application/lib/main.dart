import 'package:flutter/material.dart';

import 'core/config/app_config.dart';
import 'features/auth/auth_controller.dart';
import 'features/auth/screens/login_screen.dart';
import 'features/auth/screens/splash_screen.dart';
import 'features/auth/screens/terms_screen.dart';
import 'features/shell/app_shell.dart';
import 'features/chat/chat_screen.dart';
import 'features/s3/notification_launch_controller.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final authController = AuthController();
  await authController.bootstrap();
  runApp(LocCocApp(authController: authController));
}

class LocCocApp extends StatelessWidget {
  final AuthController authController;
  final NotificationLaunchController? notificationLaunches;

  const LocCocApp({
    super.key,
    required this.authController,
    this.notificationLaunches,
  });

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: authController,
      builder: (context, _) {
        return MaterialApp(
          title: AppConfig.appName,
          navigatorObservers: [chatRouteObserver],
          debugShowCheckedModeBanner: false,
          theme: ThemeData(
            useMaterial3: true,
            colorScheme: ColorScheme.fromSeed(
              seedColor: const Color(0xFF17365D),
              primary: const Color(0xFF17365D),
              secondary: const Color(0xFF335C8D),
              surface: const Color(0xFFF8FAFC),
            ),
            appBarTheme: const AppBarTheme(
              backgroundColor: Color(0xFF17365D),
              foregroundColor: Colors.white,
              elevation: 0,
            ),
            fontFamily: null,
          ),
          home: _resolveScreen(),
        );
      },
    );
  }

  Widget _resolveScreen() {
    switch (authController.status) {
      case AuthStatus.uninitialized:
        return const SplashScreen();
      case AuthStatus.unauthenticated:
        return LoginScreen(authController: authController);
      case AuthStatus.requiresTerms:
        return TermsScreen(authController: authController);
      case AuthStatus.authenticated:
        return AppShell(
          key: ValueKey(authController.currentUser?.id),
          authController: authController,
          notificationLaunches: notificationLaunches,
        );
    }
  }
}
