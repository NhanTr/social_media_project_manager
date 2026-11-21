import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../storage/session_storage.dart';

class ApiResponse<T> {
  final bool success;
  final int statusCode;
  final T? data;
  final String? errorMessage;
  final String? errorCode;

  const ApiResponse({
    required this.success,
    required this.statusCode,
    this.data,
    this.errorMessage,
    this.errorCode,
  });
}

class ApiClient {
  final http.Client _client;
  final SessionStorage _storage;
  Completer<String?>? _refreshCompleter;
  void Function()? onSessionExpired;

  ApiClient({
    http.Client? client,
    SessionStorage? storage,
    this.onSessionExpired,
  }) : _client = client ?? http.Client(),
       _storage = storage ?? SessionStorage();

  Uri _uri(String path, [Map<String, String>? queryParams]) {
    final base = AppConfig.baseUrl.endsWith('/')
        ? AppConfig.baseUrl.substring(0, AppConfig.baseUrl.length - 1)
        : AppConfig.baseUrl;
    final normalizedPath = path.startsWith('/') ? path : '/$path';
    final fullUrl = '$base$normalizedPath';
    final parsed = Uri.parse(fullUrl);
    if (queryParams != null && queryParams.isNotEmpty) {
      return parsed.replace(queryParameters: queryParams);
    }
    return parsed;
  }

  Map<String, String> _buildHeaders({String? token}) {
    final headers = <String, String>{
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'X-Request-Id': 'req-${DateTime.now().millisecondsSinceEpoch}',
    };
    if (token != null && token.isNotEmpty) {
      headers['Authorization'] = 'Bearer $token';
    }
    return headers;
  }

  Future<bool> checkHealth() async {
    try {
      final uri = _uri('/actuator/health');
      final res = await _client.get(uri).timeout(const Duration(seconds: 3));
      return res.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  Future<ApiResponse<dynamic>> get(
    String path, {
    Map<String, String>? queryParams,
  }) async {
    return _sendRequest(
      (token) => _client.get(
        _uri(path, queryParams),
        headers: _buildHeaders(token: token),
      ),
    );
  }

  Future<ApiResponse<dynamic>> post(String path, {dynamic body}) async {
    final payload = body != null ? jsonEncode(body) : null;
    return _sendRequest(
      (token) => _client.post(
        _uri(path),
        headers: _buildHeaders(token: token),
        body: payload,
      ),
    );
  }

  Future<ApiResponse<dynamic>> put(String path, {dynamic body}) async {
    final payload = body != null ? jsonEncode(body) : null;
    return _sendRequest(
      (token) => _client.put(
        _uri(path),
        headers: _buildHeaders(token: token),
        body: payload,
      ),
    );
  }

  Future<ApiResponse<dynamic>> delete(String path) async {
    return _sendRequest(
      (token) =>
          _client.delete(_uri(path), headers: _buildHeaders(token: token)),
    );
  }

  Future<bool> refreshAccessToken() async {
    return await _refreshTokenSingleFlight() != null;
  }

  Future<ApiResponse<dynamic>> _sendRequest(
    Future<http.Response> Function(String? token) execute,
  ) async {
    try {
      String? token = await _storage.getAccessToken();
      var response = await execute(token).timeout(const Duration(seconds: 15));

      // Handle 401 with single-flight automatic token refresh
      if (response.statusCode == 401 && token != null) {
        final newToken = await _refreshTokenSingleFlight();
        if (newToken != null) {
          response = await execute(newToken)
              .timeout(const Duration(seconds: 15));
        } else {
          await _storage.clear();
          onSessionExpired?.call();
          return const ApiResponse(
            success: false,
            statusCode: 401,
            errorMessage: 'Phiên đăng nhập đã hết hạn.',
            errorCode: 'UNAUTHORIZED',
          );
        }
      }

      if (response.statusCode == 401 && token != null) {
        await _storage.clear();
        onSessionExpired?.call();
      }
      return _parseResponse(response);
    } catch (e) {
      return ApiResponse(
        success: false,
        statusCode: 0,
        errorMessage: 'Không thể kết nối đến máy chủ: $e',
        errorCode: 'NETWORK_ERROR',
      );
    }
  }

  Future<String?> _refreshTokenSingleFlight() async {
    if (_refreshCompleter != null) {
      return _refreshCompleter!.future;
    }

    _refreshCompleter = Completer<String?>();
    try {
      final refreshToken = await _storage.getRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        _refreshCompleter!.complete(null);
        return null;
      }

      final uri = _uri('/api/v1/auth/refresh');
      final res = await _client
          .post(
            uri,
            headers: _buildHeaders(),
            body: jsonEncode({'refreshToken': refreshToken}),
          )
          .timeout(const Duration(seconds: 15));

      if (res.statusCode == 200) {
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        final newAccess = data['accessToken'] as String?;
        final newRefresh = data['refreshToken'] as String?;
        if (newAccess != null && newRefresh != null) {
          await _storage.updateTokens(
            accessToken: newAccess,
            refreshToken: newRefresh,
          );
          _refreshCompleter!.complete(newAccess);
          return newAccess;
        }
      }

      _refreshCompleter!.complete(null);
      return null;
    } catch (_) {
      _refreshCompleter!.complete(null);
      return null;
    } finally {
      _refreshCompleter = null;
    }
  }

  ApiResponse<dynamic> _parseResponse(http.Response res) {
    dynamic parsedBody;
    if (res.body.isNotEmpty) {
      try {
        parsedBody = jsonDecode(res.body);
      } catch (_) {
        parsedBody = res.body;
      }
    }

    if (res.statusCode >= 200 && res.statusCode < 300) {
      return ApiResponse(
        success: true,
        statusCode: res.statusCode,
        data: parsedBody,
      );
    }

    String? errorMsg;
    String? errCode;
    if (parsedBody is Map<String, dynamic>) {
      errorMsg =
          parsedBody['detail'] as String? ??
          parsedBody['message'] as String? ??
          parsedBody['error'] as String?;
      errCode = parsedBody['code'] as String?;
    }

    return ApiResponse(
      success: false,
      statusCode: res.statusCode,
      data: parsedBody,
      errorMessage: errorMsg ?? 'Yêu cầu không thành công (${res.statusCode})',
      errorCode: errCode,
    );
  }
}
