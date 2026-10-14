import 'package:flutter/foundation.dart';
import '../../core/models/user_models.dart';
import '../../core/network/api_client.dart';
import '../../core/storage/session_storage.dart';

enum AuthStatus {
  uninitialized,
  authenticated,
  requiresTerms,
  unauthenticated,
}

class AuthController extends ChangeNotifier {
  final ApiClient _api;
  final SessionStorage _storage;

  AuthStatus _status = AuthStatus.uninitialized;
  UserModel? _currentUser;
  bool _isLoading = false;
  String? _errorMessage;
  String? _lastOtpMock;
  bool _isGatewayHealthy = false;

  AuthController({
    ApiClient? api,
    SessionStorage? storage,
  })  : _storage = storage ?? SessionStorage(),
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
            if (uid != null && uname != null) {
              _currentUser = UserModel(id: uid, username: uname, displayName: dname ?? uname);
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
      final res = await _api.post('/api/v1/auth/phone/send-otp', body: {
        'phoneNumber': phoneNumber.trim(),
      });

      if (res.success && res.data is Map<String, dynamic>) {
        final data = res.data as Map<String, dynamic>;
        _lastOtpMock = data['otpCode']?.toString();
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
      final res = await _api.post('/api/v1/auth/phone/verify-otp', body: {
        'phoneNumber': phoneNumber.trim(),
        'otpCode': otpCode.trim(),
      });

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

  Future<bool> loginWithGoogle([String? mockToken]) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final token = mockToken ?? 'mock-google-id-token-${DateTime.now().millisecondsSinceEpoch}';
      final res = await _api.post('/api/v1/auth/google', body: {
        'idToken': token,
      });

      if (res.success && res.data is Map<String, dynamic>) {
        final session = AuthSession.fromJson(res.data as Map<String, dynamic>);
        await _saveAuthSession(session);
        return true;
      } else {
        _errorMessage = res.errorMessage ?? 'Đăng nhập Google không thành công.';
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
    return const TermsModel(id: 1, version: 'v1.0.0', contentUrl: '');
  }

  Future<bool> acceptTerms(int termsVersionId) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final res = await _api.post('/api/v1/terms/accept', body: {
        'termsVersionId': termsVersionId,
      });

      if (res.success) {
        await _storage.setTermsAccepted();
        _status = AuthStatus.authenticated;
        await refreshProfile();
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

  Future<bool> updateProfile({String? displayName, String? bio, String? avatarUrl}) async {
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
