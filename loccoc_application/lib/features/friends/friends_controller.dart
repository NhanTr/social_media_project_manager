import 'package:flutter/foundation.dart';
import '../../core/models/user_models.dart';
import '../../core/network/api_client.dart';

class FriendsController extends ChangeNotifier {
  final ApiClient api;

  List<FriendSummary> _friends = [];
  List<FriendRequestModel> _incomingRequests = [];
  List<FriendRequestModel> _outgoingRequests = [];
  List<BlockModel> _blockedUsers = [];
  List<SearchResultModel> _searchResults = [];

  bool _isLoading = false;
  bool _isSearching = false;
  String? _errorMessage;

  FriendsController({required this.api});

  List<FriendSummary> get friends => _friends;
  List<FriendRequestModel> get incomingRequests => _incomingRequests;
  List<FriendRequestModel> get outgoingRequests => _outgoingRequests;
  List<BlockModel> get blockedUsers => _blockedUsers;
  List<SearchResultModel> get searchResults => _searchResults;
  bool get isLoading => _isLoading;
  bool get isSearching => _isSearching;
  String? get errorMessage => _errorMessage;

  Future<void> loadAll() async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      await Future.wait([
        loadFriends(),
        loadIncomingRequests(),
        loadOutgoingRequests(),
        loadBlockedUsers(),
      ]);
    } catch (e) {
      _errorMessage = 'Không thể tải dữ liệu bạn bè: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> loadFriends() async {
    final res = await api.get('/api/v1/friends');
    if (res.success && res.data is List) {
      _friends = (res.data as List)
          .map((item) => FriendSummary.fromJson(item as Map<String, dynamic>))
          .toList();
      notifyListeners();
    }
  }

  Future<void> loadIncomingRequests() async {
    final res = await api.get('/api/v1/friend-requests/incoming');
    if (res.success && res.data is List) {
      _incomingRequests = (res.data as List)
          .map((item) => FriendRequestModel.fromJson(item as Map<String, dynamic>))
          .toList();
      notifyListeners();
    }
  }

  Future<void> loadOutgoingRequests() async {
    final res = await api.get('/api/v1/friend-requests/outgoing');
    if (res.success && res.data is List) {
      _outgoingRequests = (res.data as List)
          .map((item) => FriendRequestModel.fromJson(item as Map<String, dynamic>))
          .toList();
      notifyListeners();
    }
  }

  Future<void> loadBlockedUsers() async {
    final res = await api.get('/api/v1/blocks');
    if (res.success && res.data is List) {
      _blockedUsers = (res.data as List)
          .map((item) => BlockModel.fromJson(item as Map<String, dynamic>))
          .toList();
      notifyListeners();
    }
  }

  Future<void> searchUsers(String query) async {
    if (query.trim().isEmpty) {
      _searchResults = [];
      _isSearching = false;
      notifyListeners();
      return;
    }

    _isSearching = true;
    notifyListeners();

    try {
      final res = await api.get('/api/v1/friends/search', queryParams: {'keyword': query.trim()});
      if (res.success && res.data is List) {
        _searchResults = (res.data as List)
            .map((item) => SearchResultModel.fromJson(item as Map<String, dynamic>))
            .toList();
      } else {
        _searchResults = [];
      }
    } catch (_) {
      _searchResults = [];
    } finally {
      _isSearching = false;
      notifyListeners();
    }
  }

  void clearSearch() {
    _searchResults = [];
    _isSearching = false;
    notifyListeners();
  }

  Future<bool> sendFriendRequest(int receiverId) async {
    final res = await api.post('/api/v1/friend-requests', body: {'receiverId': receiverId});
    if (res.success) {
      await loadOutgoingRequests();
      return true;
    } else {
      _errorMessage = res.errorMessage ?? 'Không thể gửi lời mời kết bạn.';
      notifyListeners();
      return false;
    }
  }

  Future<bool> acceptFriendRequest(int requestId) async {
    // Optimistic update
    final pending = _incomingRequests.firstWhere((r) => r.id == requestId, orElse: () => FriendRequestModel(id: requestId, senderId: 0, receiverId: 0, status: ''));
    _incomingRequests.removeWhere((r) => r.id == requestId);
    notifyListeners();

    final res = await api.post('/api/v1/friend-requests/$requestId/accept');
    if (res.success) {
      await loadFriends();
      return true;
    } else {
      _incomingRequests.add(pending);
      _errorMessage = res.errorMessage ?? 'Không thể chấp nhận lời mời.';
      notifyListeners();
      return false;
    }
  }

  Future<bool> declineFriendRequest(int requestId) async {
    final res = await api.post('/api/v1/friend-requests/$requestId/decline');
    if (res.success) {
      _incomingRequests.removeWhere((r) => r.id == requestId);
      notifyListeners();
      return true;
    } else {
      _errorMessage = res.errorMessage ?? 'Không thể từ chối lời mời.';
      notifyListeners();
      return false;
    }
  }

  Future<bool> blockUser(int userId) async {
    final res = await api.post('/api/v1/blocks/$userId');
    if (res.success) {
      _friends.removeWhere((f) => f.id == userId);
      _incomingRequests.removeWhere((r) => r.senderId == userId);
      _outgoingRequests.removeWhere((r) => r.receiverId == userId);
      await loadBlockedUsers();
      notifyListeners();
      return true;
    } else {
      _errorMessage = res.errorMessage ?? 'Không thể chặn người dùng.';
      notifyListeners();
      return false;
    }
  }

  Future<bool> unblockUser(int userId) async {
    final res = await api.delete('/api/v1/blocks/$userId');
    if (res.success) {
      _blockedUsers.removeWhere((b) => b.blockedId == userId);
      notifyListeners();
      return true;
    } else {
      _errorMessage = res.errorMessage ?? 'Không thể bỏ chặn.';
      notifyListeners();
      return false;
    }
  }
}
