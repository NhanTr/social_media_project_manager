import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:loccoc_application/features/friends/friends_controller.dart';
import 'package:loccoc_application/core/network/api_client.dart';

class FriendsApi extends ApiClient {
  bool fail = false;
  @override
  Future<ApiResponse<dynamic>> get(
    String path, {
    Map<String, String>? queryParams,
  }) async => fail
      ? const ApiResponse(success: false, statusCode: 0)
      : ApiResponse(
          success: true,
          statusCode: 200,
          data: path == '/api/v1/friends'
              ? [
                  {'id': 2, 'username': 'mai', 'displayName': 'Mai'},
                ]
              : [],
        );
  @override
  Future<ApiResponse<dynamic>> post(String path, {dynamic body}) async =>
      const ApiResponse(success: true, statusCode: 200);
}

class PendingFriendsApi extends FriendsApi {
  final response = Completer<ApiResponse<dynamic>>();
  @override
  Future<ApiResponse<dynamic>> get(
    String path, {
    Map<String, String>? queryParams,
  }) => response.future;
}

void main() {
  test(
    'friend feed invalidation only uses authoritative friend snapshots',
    () async {
      final api = FriendsApi();
      final friends = FriendsController(api: api);
      expect(friends.hasLoadedFriends, isFalse);
      api.fail = true;
      await friends.loadFriends();
      expect(friends.hasLoadedFriends, isFalse);
      api.fail = false;
      await friends.loadFriends();
      expect(friends.hasLoadedFriends, isTrue);
      expect(friends.friends.single.id, 2);
      await friends.blockUser(2);
      expect(friends.friends, isEmpty);
      friends.dispose();
    },
  );
  test(
    'late friend response after shell disposal does not notify disposed state',
    () async {
      final api = PendingFriendsApi();
      final controller = FriendsController(api: api);
      final request = controller.loadFriends();
      controller.dispose();
      api.response.complete(
        const ApiResponse(success: true, statusCode: 200, data: []),
      );
      await expectLater(request, completes);
    },
  );
}
