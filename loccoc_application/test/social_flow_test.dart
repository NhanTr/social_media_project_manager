import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:loccoc_application/core/network/api_client.dart';
import 'package:loccoc_application/core/storage/session_storage.dart';
import 'package:loccoc_application/features/friends/friends_controller.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
  });
  test(
    'cancel removes outgoing request and suppresses concurrent duplicate',
    () async {
      final response = Completer<http.Response>();
      final started = Completer<void>();
      var writes = 0;
      final ctrl = FriendsController(
        api: ApiClient(
          client: MockClient((req) async {
            if (req.method == 'POST') {
              expect(req.url.path, '/api/v1/friend-requests/10/cancel');
              writes++;
              started.complete();
              return response.future;
            }
            return http.Response(
              '[{"id":10,"senderId":1,"receiverId":2,"status":"PENDING"}]',
              200,
            );
          }),
        ),
      );
      await ctrl.loadOutgoingRequests();
      final first = ctrl.cancelFriendRequest(10);
      await started.future;
      expect(await ctrl.cancelFriendRequest(10), isFalse);
      response.complete(http.Response('{}', 200));
      expect(await first, isTrue);
      expect(ctrl.outgoingRequests, isEmpty);
      expect(writes, 1);
    },
  );
  test('tokens use secure storage and logout removes session', () async {
    final storage = SessionStorage();
    await storage.saveSession(
      accessToken: 'access',
      refreshToken: 'refresh',
      userId: 1,
      username: 'test',
      displayName: 'Test',
      requiresTerms: true,
    );
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('loccoc_access_token'), isNull);
    expect(prefs.getString('loccoc_refresh_token'), isNull);
    expect(await storage.getRefreshToken(), 'refresh');
    await storage.clear();
    expect(await storage.getAccessToken(), isNull);
    expect(await storage.getUserId(), isNull);
  });
}
