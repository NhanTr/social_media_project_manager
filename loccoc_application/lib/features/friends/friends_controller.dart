import 'package:flutter/foundation.dart';

import '../../core/models/user_models.dart';
import '../../core/network/api_client.dart';

class FriendsController extends ChangeNotifier {
  final ApiClient api;
  bool _disposed = false;
  void _emit() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    ++_searchVersion;
    super.dispose();
  }

  List<FriendSummary> _friends = [];
  List<FriendRequestModel> _incomingRequests = [];
  List<FriendRequestModel> _outgoingRequests = [];
  List<BlockModel> _blockedUsers = [];
  List<SearchResultModel> _searchResults = [];

  final Set<String> _pendingActions = {};
  bool isPending(String key) => _pendingActions.contains(key);
  int _searchVersion = 0;

  Future<bool> _mutate(String key, Future<bool> Function() action) async {
    if (!_pendingActions.add(key)) return false;
    _errorMessage = null;
    _canRetryRead = false;
    _emit();
    try {
      final result = await action();
      if (result && _lastQuery.isNotEmpty) await searchUsers(_lastQuery);
      return result;
    } catch (_) {
      _errorMessage = 'Không thể hoàn tất thao tác. Vui lòng thử lại.';
      return false;
    } finally {
      _pendingActions.remove(key);
      _emit();
    }
  }

  String _lastQuery = '';
  bool _hasLoadedFriends = false;
  bool get hasLoadedFriends => _hasLoadedFriends;
  bool _isLoading = false;
  bool _isSearching = false;
  String? _errorMessage;
  bool _canRetryRead = false;

  FriendsController({required this.api});

  List<FriendSummary> get friends => _friends;
  List<FriendRequestModel> get incomingRequests => _incomingRequests;
  List<FriendRequestModel> get outgoingRequests => _outgoingRequests;
  List<BlockModel> get blockedUsers => _blockedUsers;
  List<SearchResultModel> get searchResults => _searchResults;
  bool get isLoading => _isLoading;
  bool get isSearching => _isSearching;
  String? get errorMessage => _errorMessage;
  bool get canRetryRead => _canRetryRead;

  Future<void> loadAll() async {
    _isLoading = true;
    _errorMessage = null;
    _canRetryRead = false;
    _emit();

    try {
      await Future.wait([
        loadFriends(),
        loadIncomingRequests(),
        loadOutgoingRequests(),
        loadBlockedUsers(),
      ]);
    } catch (e) {
      _errorMessage = 'Không thể tải dữ liệu bạn bè: $e';
      _canRetryRead = true;
    } finally {
      _isLoading = false;
      _emit();
    }
  }

  Future<void> loadFriends() async {
    final res = await api.get('/api/v1/friends');
    if (!res.success) {
      _errorMessage =
          res.errorMessage ?? 'Không thể tải dữ liệu. Vui lòng thử lại.';
      _canRetryRead = true;
      _emit();
    }
    if (res.success && res.data is List) {
      _hasLoadedFriends = true;
      _friends = (res.data as List)
          .map((item) => FriendSummary.fromJson(item as Map<String, dynamic>))
          .toList();
      _emit();
    }
  }

  Future<void> loadIncomingRequests() async {
    final res = await api.get('/api/v1/friend-requests/incoming');
    if (!res.success) {
      _errorMessage =
          res.errorMessage ?? 'Không thể tải dữ liệu. Vui lòng thử lại.';
      _canRetryRead = true;
      _emit();
    }
    if (res.success && res.data is List) {
      _incomingRequests = (res.data as List)
          .map(
            (item) => FriendRequestModel.fromJson(item as Map<String, dynamic>),
          )
          .toList();
      _emit();
    }
  }

  Future<void> loadOutgoingRequests() async {
    final res = await api.get('/api/v1/friend-requests/outgoing');
    if (!res.success) {
      _errorMessage =
          res.errorMessage ?? 'Không thể tải dữ liệu. Vui lòng thử lại.';
      _canRetryRead = true;
      _emit();
    }
    if (res.success && res.data is List) {
      _outgoingRequests = (res.data as List)
          .map(
            (item) => FriendRequestModel.fromJson(item as Map<String, dynamic>),
          )
          .toList();
      _emit();
    }
  }

  Future<void> loadBlockedUsers() async {
    final res = await api.get('/api/v1/blocks');
    if (!res.success) {
      _errorMessage =
          res.errorMessage ?? 'Không thể tải dữ liệu. Vui lòng thử lại.';
      _canRetryRead = true;
      _emit();
    }
    if (res.success && res.data is List) {
      _blockedUsers = (res.data as List)
          .map((item) => BlockModel.fromJson(item as Map<String, dynamic>))
          .toList();
      _emit();
    }
  }

  Future<void> searchUsers(String query) async {
    final version = ++_searchVersion;
    _lastQuery = query.trim();
    _errorMessage = null;
    _canRetryRead = false;
    if (query.trim().isEmpty) {
      _searchResults = [];
      _isSearching = false;
      _emit();
      return;
    }

    _isSearching = true;
    _emit();

    try {
      final res = await api.get(
        '/api/v1/friends/search',
        queryParams: {'keyword': query.trim()},
      );
      if (version != _searchVersion) return;
      if (!res.success) {
        _errorMessage =
            res.errorMessage ?? 'Không thể tải dữ liệu. Vui lòng thử lại.';
        _canRetryRead = true;
        _emit();
      }
      if (res.success && res.data is List) {
        _searchResults = (res.data as List)
            .map(
              (item) =>
                  SearchResultModel.fromJson(item as Map<String, dynamic>),
            )
            .toList();
      } else {
        _searchResults = [];
      }
    } catch (_) {
      _searchResults = [];
      _errorMessage = 'Không thể tải dữ liệu. Vui lòng thử lại.';
      _canRetryRead = true;
    } finally {
      if (version == _searchVersion) {
        _isSearching = false;
        _emit();
      }
    }
  }

  void clearSearch() {
    _searchVersion++;
    _lastQuery = "";
    _searchResults = [];
    _isSearching = false;
    _errorMessage = null;
    _canRetryRead = false;
    _emit();
  }

  Future<bool> sendFriendRequest(int receiverId) =>
      _mutate('user:$receiverId', () => _sendFriendRequest(receiverId));

  Future<bool> _sendFriendRequest(int receiverId) async {
    final res = await api.post(
      '/api/v1/friend-requests',
      body: {'receiverId': receiverId},
    );
    if (res.success) {
      await loadOutgoingRequests();
      return true;
    } else {
      _errorMessage = res.errorMessage ?? 'Không thể gửi lời mời kết bạn.';
      _emit();
      return false;
    }
  }

  Future<bool> acceptFriendRequest(int requestId) =>
      _mutate('request:$requestId', () => _acceptFriendRequest(requestId));

  Future<bool> _acceptFriendRequest(int requestId) async {
    // Optimistic update
    final index = _incomingRequests.indexWhere((r) => r.id == requestId);
    if (index < 0) return false;
    final pending = _incomingRequests[index];
    _incomingRequests.removeWhere((r) => r.id == requestId);
    _emit();

    final res = await api.post('/api/v1/friend-requests/$requestId/accept');
    if (res.success) {
      await loadFriends();
      return true;
    } else {
      if (!_incomingRequests.any((r) => r.id == requestId)) {
        _incomingRequests.insert(
          index.clamp(0, _incomingRequests.length),
          pending,
        );
      }
      _errorMessage = res.errorMessage ?? 'Không thể chấp nhận lời mời.';
      _emit();
      return false;
    }
  }

  Future<bool> declineFriendRequest(int requestId) =>
      _mutate('request:$requestId', () => _declineFriendRequest(requestId));

  Future<bool> _declineFriendRequest(int requestId) async {
    final res = await api.post('/api/v1/friend-requests/$requestId/decline');
    if (res.success) {
      _incomingRequests.removeWhere((r) => r.id == requestId);
      _emit();
      return true;
    } else {
      _errorMessage = res.errorMessage ?? 'Không thể từ chối lời mời.';
      _emit();
      return false;
    }
  }

  Future<bool> cancelFriendRequest(int requestId) =>
      _mutate('request:$requestId', () async {
        final res = await api.post('/api/v1/friend-requests/$requestId/cancel');
        if (!res.success) {
          _errorMessage = res.errorMessage ?? 'Không thể hủy lời mời.';
          return false;
        }
        _outgoingRequests.removeWhere((r) => r.id == requestId);
        _emit();
        return true;
      });

  Future<bool> blockUser(int userId) =>
      _mutate('user:$userId', () => _blockUser(userId));

  Future<bool> _blockUser(int userId) async {
    final res = await api.post('/api/v1/blocks/$userId');
    if (res.success) {
      _friends.removeWhere((f) => f.id == userId);
      _incomingRequests.removeWhere((r) => r.senderId == userId);
      _outgoingRequests.removeWhere((r) => r.receiverId == userId);
      await loadBlockedUsers();
      _emit();
      return true;
    } else {
      _errorMessage = res.errorMessage ?? 'Không thể chặn người dùng.';
      _emit();
      return false;
    }
  }

  Future<bool> unblockUser(int userId) =>
      _mutate('user:$userId', () => _unblockUser(userId));

  Future<bool> _unblockUser(int userId) async {
    final res = await api.delete('/api/v1/blocks/$userId');
    if (res.success) {
      _blockedUsers.removeWhere((b) => b.blockedId == userId);
      _emit();
      return true;
    } else {
      _errorMessage = res.errorMessage ?? 'Không thể bỏ chặn.';
      _emit();
      return false;
    }
  }
}
