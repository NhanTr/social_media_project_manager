import 'dart:async';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:url_launcher/url_launcher.dart';

import 'subscription_api.dart';
import 'subscription_models.dart';

Future<bool> _openCheckout(Uri url) =>
    launchUrl(url, mode: LaunchMode.externalApplication);

class SubscriptionController extends ChangeNotifier {
  final SubscriptionApi api;
  final Future<bool> Function(Uri) _launchCheckout;
  final DateTime Function() _now;
  SubscriptionController({
    required this.api,
    Future<bool> Function(Uri)? launchCheckout,
    DateTime Function()? now,
  }) : _launchCheckout = launchCheckout ?? _openCheckout,
       _now = now ?? DateTime.now;

  List<SubscriptionPlan> plans = const [];
  SubscriptionSnapshot? snapshot;
  String? error;
  bool busy = false;
  bool refreshing = false;
  bool _disposed = false;
  bool _fresh = false;
  bool _checkoutPending = false;
  Future<void>? _refresh;
  CheckoutSession? _checkout;
  String? _requestId;
  Timer? _expiryTimer;
  Timer? _pollTimer;
  int _pollsRemaining = 0;

  bool get canUseAi =>
      !_disposed &&
      _fresh &&
      !refreshing &&
      snapshot?.aiGeneration == true &&
      snapshot!.validUntil.isAfter(_now());

  bool get paymentPending =>
      _checkoutPending || snapshot?.paymentStatus == 'PENDING';

  bool get paymentFailed =>
      !paymentPending && snapshot?.paymentStatus == 'FAILED';

  bool get canCheckout =>
      !_disposed &&
      !busy &&
      !refreshing &&
      _fresh &&
      snapshot != null &&
      snapshot!.validUntil.isAfter(_now()) &&
      snapshot!.tier == 'FREE' &&
      plans.any((p) => p.tier == 'PLUS' && p.sandbox && p.checkoutEnabled);

  bool get canCancel =>
      !_disposed &&
      !busy &&
      !refreshing &&
      _fresh &&
      snapshot?.tier == 'PLUS' &&
      snapshot?.cancelAtPeriodEnd == false &&
      snapshot!.validUntil.isAfter(_now()) &&
      {'ACTIVE', 'GRACE', 'PAST_DUE'}.contains(snapshot!.status);

  void _emit() {
    if (!_disposed) notifyListeners();
  }

  Future<void> refresh() {
    if (_disposed) return Future.value();
    return _refresh ??= _load().whenComplete(() => _refresh = null);
  }

  Future<void> _load() async {
    refreshing = true;
    error = null;
    _emit();
    try {
      // Publish the catalog and account state atomically; never mix versions.
      final result = await Future.wait<Object>([api.plans(), api.current()]);
      if (_disposed) return;
      plans = result[0] as List<SubscriptionPlan>;
      snapshot = result[1] as SubscriptionSnapshot;
      _fresh = true;
      if ({'FAILED', 'SUCCEEDED'}.contains(snapshot!.paymentStatus) &&
          (_requestId == null ||
              (_checkout != null &&
                  snapshot!.paymentSessionId == _checkout!.sessionId))) {
        _checkoutPending = false;
        _checkout = null;
        _requestId = null;
      }
      if (!paymentPending) {
        _pollTimer?.cancel();
        _pollsRemaining = 0;
      }
      _expiryTimer?.cancel();
      final lifetime = snapshot!.validUntil.difference(_now());
      if (lifetime > Duration.zero) {
        _expiryTimer = Timer(lifetime, _emit);
      }
    } catch (_) {
      if (_disposed) return;
      _fresh = false;
      error = 'Không thể tải quyền lợi. Vui lòng thử lại.';
    } finally {
      if (!_disposed) {
        refreshing = false;
        _emit();
      }
    }
  }

  String _newRequestId() {
    final random = Random.secure();
    final bytes = List<int>.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }

  Future<void> startCheckout() async {
    if (!canCheckout) return;
    busy = true;
    error = null;
    _emit();
    try {
      if (_checkout != null && !_checkout!.expiresAt.isAfter(_now())) {
        _checkout = null;
        _requestId = null;
      }
      _requestId ??= _newRequestId();
      final session = _checkout ?? await api.checkout(_requestId!);
      if (_disposed) return;
      if (!session.expiresAt.isAfter(_now())) {
        _requestId = null;
        throw const SubscriptionException(410, 'CHECKOUT_EXPIRED');
      }
      _checkout = session;
      // Browser completion is never an entitlement signal.
      _checkoutPending = true;
      if (!await _launchCheckout(session.url)) {
        throw const SubscriptionException(0, 'LAUNCH_FAILED');
      }
    } on SubscriptionException catch (e) {
      if (!_disposed) {
        if (e.statusCode == 410) {
          _checkout = null;
          _requestId = null;
          _checkoutPending = false;
        }
        error = 'Không mở được thanh toán sandbox. Vui lòng thử lại.';
      }
    } catch (_) {
      if (!_disposed) {
        error = 'Không mở được thanh toán sandbox. Vui lòng thử lại.';
      }
    } finally {
      if (!_disposed) {
        busy = false;
        _emit();
      }
    }
  }

  /// Called on app resume/return, with no trusted callback parameters.
  Future<void> reconcileCheckout() async {
    if (_disposed || busy) return;
    _pollTimer?.cancel();
    _pollsRemaining = 4;
    await refresh();
    _schedulePoll();
  }

  void _schedulePoll() {
    if (_disposed || !paymentPending || _pollsRemaining <= 0) return;
    _pollTimer = Timer(const Duration(seconds: 3), () async {
      _pollsRemaining--;
      if (!busy) await refresh();
      _schedulePoll();
    });
  }

  Future<void> cancelAtPeriodEnd() async {
    if (!canCancel) return;
    busy = true;
    error = null;
    _emit();
    try {
      await api.cancelAtPeriodEnd();
      if (!_disposed) await refresh();
    } catch (_) {
      if (!_disposed) error = 'Chưa xác nhận được việc hủy gia hạn. Hãy làm mới trước khi thử lại.';
    } finally {
      if (!_disposed) {
        busy = false;
        _emit();
      }
    }
  }

  Future<AiGeneration?> generateAi(String prompt) async {
    final text = prompt.trim();
    if (_disposed || busy || refreshing || text.isEmpty || text.length > 1000) {
      return null;
    }
    busy = true;
    error = null;
    _emit();
    try {
      await refresh();
      if (!canUseAi) {
        if (!_disposed) {
          error ??=
              'Tài khoản chưa có quyền tạo ảnh AI. Hãy kiểm tra gói của bạn.';
        }
        return null;
      }
      final generation = await api.createAi(text);
      return _disposed ? null : generation;
    } on SubscriptionException catch (e) {
      if (!_disposed) {
        if (e.statusCode == 403 || e.statusCode == 401) _fresh = false;
        error = 'Chưa tạo được ảnh AI. Kiểm tra trạng thái trước khi gửi lại.';
      }
      return null;
    } finally {
      if (!_disposed) {
        busy = false;
        _emit();
      }
    }
  }

  Future<AiGeneration?> loadAi(int id) async {
    if (_disposed || busy || refreshing || id <= 0) return null;
    busy = true;
    error = null;
    _emit();
    try {
      await refresh();
      if (!canUseAi) return null;
      final generation = await api.aiGeneration(id);
      return _disposed ? null : generation;
    } on SubscriptionException catch (e) {
      if (!_disposed) {
        if (e.statusCode == 403 || e.statusCode == 401) _fresh = false;
        error = 'Không thể tải trạng thái ảnh AI.';
      }
      return null;
    } finally {
      if (!_disposed) {
        busy = false;
        _emit();
      }
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _fresh = false;
    snapshot = null;
    plans = const [];
    _checkout = null;
    _requestId = null;
    _expiryTimer?.cancel();
    _pollTimer?.cancel();
    super.dispose();
  }
}
