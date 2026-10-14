import 'package:flutter/material.dart';
import '../../auth/auth_controller.dart';
import '../../../core/config/app_config.dart';

class ProfileScreen extends StatefulWidget {
  final AuthController authController;

  const ProfileScreen({super.key, required this.authController});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  late TextEditingController _displayNameController;
  late TextEditingController _bioController;
  bool _isEditing = false;

  @override
  void initState() {
    super.initState();
    final user = widget.authController.currentUser;
    _displayNameController = TextEditingController(text: user?.displayName ?? '');
    _bioController = TextEditingController(text: user?.bio ?? '');
  }

  @override
  void dispose() {
    _displayNameController.dispose();
    _bioController.dispose();
    super.dispose();
  }

  Future<void> _handleSave() async {
    final dName = _displayNameController.text.trim();
    final bio = _bioController.text.trim();

    final ok = await widget.authController.updateProfile(
      displayName: dName,
      bio: bio,
    );

    if (ok && mounted) {
      setState(() => _isEditing = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Hồ sơ đã được cập nhật thành công!')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.authController,
      builder: (context, _) {
        final user = widget.authController.currentUser;
        final isHealthy = widget.authController.isGatewayHealthy;

        return Scaffold(
          appBar: AppBar(
            title: const Text('Hồ sơ cá nhân', style: TextStyle(fontWeight: FontWeight.bold)),
            backgroundColor: const Color(0xFF17365D),
            foregroundColor: Colors.white,
            actions: [
              IconButton(
                icon: Icon(_isEditing ? Icons.close : Icons.edit),
                tooltip: _isEditing ? 'Hủy chỉnh sửa' : 'Chỉnh sửa hồ sơ',
                onPressed: () {
                  setState(() {
                    _isEditing = !_isEditing;
                    if (!_isEditing) {
                      _displayNameController.text = user?.displayName ?? '';
                      _bioController.text = user?.bio ?? '';
                    }
                  });
                },
              ),
              IconButton(
                icon: const Icon(Icons.logout),
                tooltip: 'Đăng xuất',
                onPressed: () => widget.authController.logout(),
              ),
            ],
          ),
          body: SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: Column(
              children: [
                // Avatar and Display Info
                Center(
                  child: Stack(
                    children: [
                      CircleAvatar(
                        radius: 48,
                        backgroundColor: const Color(0xFF17365D),
                        foregroundColor: Colors.white,
                        child: Text(
                          user?.displayName.isNotEmpty == true ? user!.displayName[0].toUpperCase() : 'U',
                          style: const TextStyle(fontSize: 36, fontWeight: FontWeight.bold),
                        ),
                      ),
                      Positioned(
                        bottom: 0,
                        right: 0,
                        child: Container(
                          padding: const EdgeInsets.all(4),
                          decoration: BoxDecoration(
                            color: Colors.green,
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 2),
                          ),
                          child: const Icon(Icons.check, size: 12, color: Colors.white),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  user?.displayName ?? 'Người dùng LocCoc',
                  style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Color(0xFF17365D)),
                ),
                const SizedBox(height: 4),
                Text(
                  '@${user?.username ?? "user"} • ID: #${user?.id ?? 0}',
                  style: TextStyle(fontSize: 14, color: Colors.grey.shade600),
                ),
                const SizedBox(height: 16),

                // Edit Profile Form or View Profile Info
                Card(
                  elevation: 1,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const Text(
                          'Thông tin tài khoản',
                          style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Color(0xFF17365D)),
                        ),
                        const SizedBox(height: 12),
                        if (_isEditing) ...[
                          TextField(
                            controller: _displayNameController,
                            decoration: const InputDecoration(
                              labelText: 'Tên hiển thị',
                              border: OutlineInputBorder(),
                            ),
                          ),
                          const SizedBox(height: 12),
                          TextField(
                            controller: _bioController,
                            maxLines: 3,
                            decoration: const InputDecoration(
                              labelText: 'Tiểu sử (Bio)',
                              border: OutlineInputBorder(),
                            ),
                          ),
                          const SizedBox(height: 16),
                          FilledButton(
                            onPressed: widget.authController.isLoading ? null : _handleSave,
                            style: FilledButton.styleFrom(backgroundColor: const Color(0xFF17365D)),
                            child: const Text('Lưu thay đổi'),
                          ),
                        ] else ...[
                          _buildInfoRow('Tên hiển thị', user?.displayName ?? 'Chưa đặt'),
                          _buildInfoRow('Số điện thoại', user?.phoneNumber ?? 'Chưa liên kết'),
                          _buildInfoRow('Email', user?.email ?? 'Chưa liên kết'),
                          _buildInfoRow('Mã mời bạn bè', user?.friendInviteCode ?? 'LOCCOC-${user?.id ?? 0}'),
                          _buildInfoRow('Tiểu sử', user?.bio ?? 'Chưa có tiểu sử'),
                          _buildInfoRow('Trạng thái', user?.status ?? 'ACTIVE'),
                        ],
                      ],
                    ),
                  ),
                ),

                const SizedBox(height: 16),

                // Gateway & Network Info
                Card(
                  elevation: 0,
                  color: const Color(0xFFF3F7FB),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Thông tin hệ thống',
                          style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Color(0xFF17365D)),
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            Icon(
                              isHealthy ? Icons.check_circle : Icons.offline_bolt,
                              color: isHealthy ? Colors.green : Colors.orange,
                              size: 16,
                            ),
                            const SizedBox(width: 8),
                            Text(
                              isHealthy ? 'API Gateway đang hoạt động (Healthy)' : 'Chế độ Demo / Offline',
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: isHealthy ? Colors.green.shade800 : Colors.orange.shade900,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Endpoint: ${AppConfig.baseUrl}',
                          style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Phiên bản ứng dụng: ${AppConfig.appVersion}',
                          style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                        ),
                      ],
                    ),
                  ),
                ),

                const SizedBox(height: 24),
                OutlinedButton.icon(
                  onPressed: () => widget.authController.logout(),
                  icon: const Icon(Icons.logout, color: Colors.red),
                  label: const Text('Đăng xuất khỏi tài khoản', style: TextStyle(color: Colors.red)),
                  style: OutlinedButton.styleFrom(
                    side: const BorderSide(color: Colors.red),
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(label, style: const TextStyle(color: Colors.black54, fontSize: 13)),
          ),
          Expanded(
            child: Text(value, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
          ),
        ],
      ),
    );
  }
}
