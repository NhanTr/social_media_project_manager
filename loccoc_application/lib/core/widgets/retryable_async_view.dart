import 'package:flutter/material.dart';

class RetryableAsyncView extends StatelessWidget {
  static const _fallbackMessage =
      'Không thể hoàn tất yêu cầu. Vui lòng thử lại.';

  final bool isLoading;
  final String? errorMessage;
  final VoidCallback? onRetry;
  final Widget child;

  const RetryableAsyncView({
    super.key,
    required this.isLoading,
    this.errorMessage,
    this.onRetry,
    required this.child,
  });

  static String safeMessage(String message) {
    final trimmed = message.trim();
    if (trimmed.isEmpty) return _fallbackMessage;

    final containsSensitiveDetails = RegExp(
      r'(?:access[_ -]?token|refresh[_ -]?token|token|otp|internal[_ -]?key|authorization|bearer|raw)\s*[:=]|[{}]',
      caseSensitive: false,
    ).hasMatch(trimmed);
    return containsSensitiveDetails ? _fallbackMessage : trimmed;
  }

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    final message = errorMessage;
    if (message == null) return child;

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Semantics(
          liveRegion: true,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.error_outline,
                size: 40,
                color: Theme.of(context).colorScheme.error,
              ),
              const SizedBox(height: 12),
              Text(
                safeMessage(message),
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              if (onRetry != null) ...[
                const SizedBox(height: 12),
                FilledButton.tonal(
                  onPressed: onRetry,
                  child: const Text('Thử lại'),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
