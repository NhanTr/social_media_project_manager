import 'package:google_sign_in/google_sign_in.dart';

import '../../core/config/app_config.dart';

import 'package:flutter/foundation.dart';

import '../../core/models/user_models.dart';
import '../../core/network/api_client.dart';
import '../../core/storage/session_storage.dart';

enum AuthStatus { uninitialized, authenticated, requiresTerms, unauthenticated }

class AuthController extends ChangeNotifier {
  static Future<void>? _googleInitialization;
  final ApiClient _api;
  final SessionStorage _storage;

  AuthStatus _status = AuthStatus.uninitialized;
  UserModel? _currentUser;
  bool _isLoading = false;
  String? _errorMessage;
  String? _lastOtpMock;
  bool _isGatewayHealthy = false;

  AuthController({ApiClient? api, SessionStorage? storage})
    : _storage = storage ?? SessionStorage(),
      _api = api ?? ApiClient(storage: storage) {
    _api.onSessionExpired = () {
      _status = AuthStatus.unauthenticated;
      _currentUser = null;
      notifyListeners();
    };
  }

  AuthStatus get status => _status;
  UserModel? get currentUser => _currentUser;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  String? get lastOtpMock => _lastOtpMock;
  bool get isGatewayHealthy => _isGatewayHealthy;
  ApiClient get apiClient => _api;

  Future<void> bootstrap() async {
    _isLoading = true;
    notifyListeners();

    try {
      _isGatewayHealthy = await _api.checkHealth();
      final token = await _storage.getAccessToken();
      final requiresTerms = await _storage.getRequiresTerms();

      if (token != null && token.isNotEmpty) {
        if (requiresTerms) {
          _status = AuthStatus.requiresTerms;
        } else {
          final res = await _api.get('/api/v1/users/me');
          if (res.success && res.data is Map<String, dynamic>) {
            _currentUser = UserModel.fromJson(res.data as Map<String, dynamic>);
            _status = AuthStatus.authenticated;
          } else {
            // Token might be valid or refreshable, load local user info
            final uid = await _storage.getUserId();
            final uname = await _storage.getUsername();
            final dname = await _storage.getDisplayName();
            if (res.statusCode == 0 && uid != null && uname != null) {
              _currentUser = UserModel(
                id: uid,
                username: uname,
                displayName: dname ?? uname,
              );
              _status = AuthStatus.authenticated;
            } else {
              _status = AuthStatus.unauthenticated;
            }
          }
        }
      } else {
        _status = AuthStatus.unauthenticated;
      }
    } catch (_) {
      _status = AuthStatus.unauthenticated;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<bool> requestPhoneOtp(String phoneNumber) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final res = await _api.post(
        '/api/v1/auth/phone/send-otp',
        body: {'phoneNumber': phoneNumber.trim()},
      );

      if (res.success && res.data is Map<String, dynamic>) {
        final data = res.data as Map<String, dynamic>;
        _lastOtpMock = data['devOtp']?.toString();
        _isLoading = false;
        notifyListeners();
        return true;
      } else {
        _errorMessage = res.errorMessage ?? 'Gửi mã OTP thất bại.';
        _isLoading = false;
        notifyListeners();
        return false;
      }
    } catch (e) {
      _errorMessage = 'Lỗi gửi OTP: $e';
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> verifyPhoneOtp(String phoneNumber, String otpCode) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final res = await _api.post(
        '/api/v1/auth/phone/verify-otp',
        body: {'phoneNumber': phoneNumber.trim(), 'otp': otpCode.trim()},
      );

      if (res.success && res.data is Map<String, dynamic>) {
        final session = AuthSession.fromJson(res.data as Map<String, dynamic>);
        await _saveAuthSession(session);
        return true;
      } else {
        _errorMessage = res.errorMessage ?? 'Mã xác thực không hợp lệ.';
        _isLoading = false;
        notifyListeners();
        return false;
      }
    } catch (e) {
      _errorMessage = 'Lỗi xác thực: $e';
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> loginWithGoogle([String? idToken]) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      String? token = idToken;
      if (token == null) {
        if (AppConfig.googleServerClientId.isEmpty) {
          _errorMessage =
              'Đăng nhập Google chưa được cấu hình. Vui lòng dùng OTP.';
          _isLoading = false;
          notifyListeners();
          return false;
        }
        await (_googleInitialization ??= GoogleSignIn.instance.initialize(
          serverClientId: AppConfig.googleServerClientId,
          clientId: AppConfig.googleIosClientId.isEmpty
              ? null
              : AppConfig.googleIosClientId,
        ));
        final account = await GoogleSignIn.instance.authenticate();
        token = account.authentication.idToken;
        if (token == null || token.isEmpty) {
          throw StateError('Google không cung cấp ID token.');
        }
      }
      final res = await _api.post(
        '/api/v1/auth/google',
        body: {'idToken': token},
      );

      if (res.success && res.data is Map<String, dynamic>) {
        final session = AuthSession.fromJson(res.data as Map<String, dynamic>);
        await _saveAuthSession(session);
        return true;
      } else {
        _errorMessage =
            res.errorMessage ?? 'Đăng nhập Google không thành công.';
        _isLoading = false;
        notifyListeners();
        return false;
      }
    } catch (e) {
      _errorMessage = 'Lỗi kết nối: $e';
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<TermsModel?> getLatestTerms() async {
    try {
      final res = await _api.get('/api/v1/terms/latest');
      if (res.success && res.data is Map<String, dynamic>) {
        return TermsModel.fromJson(res.data as Map<String, dynamic>);
      }
    } catch (_) {}
    return null;
  }

  Future<bool> acceptTerms(int termsVersionId) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final res = await _api.post(
        '/api/v1/terms/accept',
        body: {'termsVersionId': termsVersionId},
      );

      if (res.success) {
        final refreshed = await _api.refreshAccessToken();
        if (!refreshed) {
          _status = AuthStatus.requiresTerms;
          _errorMessage = 'Đã ghi nhận điều khoản nhưng chưa thể làm mới phiên. Vui lòng thử lại.';
          _isLoading = false;
          notifyListeners();
          return false;
        }
        await _storage.setTermsAccepted();
        await refreshProfile();
        _status = AuthStatus.authenticated;
        _isLoading = false;
        notifyListeners();
        return true;
      } else {
        _errorMessage = res.errorMessage ?? 'Đồng ý điều khoản thất bại.';
        _isLoading = false;
        notifyListeners();
        return false;
      }
    } catch (e) {
      _errorMessage = 'Lỗi: $e';
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<void> refreshProfile() async {
    try {
      final res = await _api.get('/api/v1/users/me');
      if (res.success && res.data is Map<String, dynamic>) {
        _currentUser = UserModel.fromJson(res.data as Map<String, dynamic>);
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<bool> updateProfile({
    String? displayName,
    String? bio,
    String? avatarUrl,
  }) async {
    _isLoading = true;
    _errorMessage = null;
    try {
      final body = <String, dynamic>{};
      if (displayName != null) body['displayName'] = displayName;
      if (bio != null) body['bio'] = bio;
      if (avatarUrl != null) body['avatarUrl'] = avatarUrl;
      final res = await _api.put('/api/v1/users/me', body: body);

      if (res.success && res.data is Map<String, dynamic>) {
        _currentUser = UserModel.fromJson(res.data as Map<String, dynamic>);
        _isLoading = false;
        notifyListeners();
        return true;
      } else {
        _errorMessage = res.errorMessage ?? 'Cập nhật hồ sơ thất bại.';
        _isLoading = false;
        notifyListeners();
        return false;
      }
    } catch (e) {
      _errorMessage = 'Lỗi cập nhật: $e';
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<void> logout() async {
    _isLoading = true;
    notifyListeners();

    try {
      final refresh = await _storage.getRefreshToken();
      if (refresh != null) {
        await _api.post('/api/v1/auth/logout', body: {'refreshToken': refresh});
      }
    } catch (_) {}

    await _storage.clear();
    _currentUser = null;
    _status = AuthStatus.unauthenticated;
    _isLoading = false;
    notifyListeners();
  }

  Future<void> _saveAuthSession(AuthSession session) async {
    final user = session.user;
    await _storage.saveSession(
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      userId: user?.id ?? 0,
      username: user?.username ?? 'user',
      displayName: user?.displayName ?? 'User',
      requiresTerms: session.requiresTermsAcceptance,
    );

    _currentUser = user;
    if (session.requiresTermsAcceptance) {
      _status = AuthStatus.requiresTerms;
    } else {
      _status = AuthStatus.authenticated;
    }
    _isLoading = false;
    notifyListeners();
  }
}
