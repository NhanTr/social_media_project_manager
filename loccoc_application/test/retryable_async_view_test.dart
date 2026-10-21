import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/core/widgets/retryable_async_view.dart';

void main() {
  Widget app({
    required bool isLoading,
    String? errorMessage,
    VoidCallback? onRetry,
  }) {
    return MaterialApp(
      home: RetryableAsyncView(
        isLoading: isLoading,
        errorMessage: errorMessage,
        onRetry: onRetry,
        child: const Text('Nội dung đã tải'),
      ),
    );
  }

  testWidgets('shows a loading indicator instead of content', (tester) async {
    await tester.pumpWidget(app(isLoading: true));

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.text('Nội dung đã tải'), findsNothing);
  });

  testWidgets('shows a safe error and invokes retry only after one tap', (
    tester,
  ) async {
    var retries = 0;
    await tester.pumpWidget(
      app(
        isLoading: false,
        errorMessage: 'Không thể tải dữ liệu. Vui lòng thử lại.',
        onRetry: () => retries++,
      ),
    );

    expect(
      find.text('Không thể tải dữ liệu. Vui lòng thử lại.'),
      findsOneWidget,
    );
    expect(retries, 0);

    await tester.tap(find.text('Thử lại'));

    expect(retries, 1);
  });

  testWidgets('redacts sensitive-looking error details', (tester) async {
    await tester.pumpWidget(
      app(
        isLoading: false,
        errorMessage: 'token=secret otp=123456 raw={"accessToken":"secret"}',
      ),
    );

    expect(
      find.text('Không thể hoàn tất yêu cầu. Vui lòng thử lại.'),
      findsOneWidget,
    );
    expect(find.textContaining('secret'), findsNothing);
    expect(find.textContaining('123456'), findsNothing);
  });

  testWidgets('shows content when no loading or error exists', (tester) async {
    await tester.pumpWidget(app(isLoading: false));

    expect(find.text('Nội dung đã tải'), findsOneWidget);
  });

  testWidgets('omits retry when no safe callback is supplied', (tester) async {
    await tester.pumpWidget(
      app(isLoading: false, errorMessage: 'Không thể gửi lời mời.'),
    );

    expect(find.text('Không thể gửi lời mời.'), findsOneWidget);
    expect(find.text('Thử lại'), findsNothing);
  });
}
