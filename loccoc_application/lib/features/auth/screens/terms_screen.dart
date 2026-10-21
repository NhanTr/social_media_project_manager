import 'package:flutter/material.dart';

import '../../../core/models/user_models.dart';
import '../../../core/widgets/retryable_async_view.dart';
import '../auth_controller.dart';

class TermsScreen extends StatefulWidget {
  final AuthController authController;

  const TermsScreen({super.key, required this.authController});

  @override
  State<TermsScreen> createState() => _TermsScreenState();
}

class _TermsScreenState extends State<TermsScreen> {
  TermsModel? _terms;
  bool _isLoading = true;
  bool _agreed = false;
  String? _loadErrorMessage;

  @override
  void initState() {
    super.initState();
    _loadTerms();
  }

  Future<void> _loadTerms() async {
    setState(() {
      _isLoading = true;
      _loadErrorMessage = null;
    });
    final terms = await widget.authController.getLatestTerms();
    if (mounted) {
      setState(() {
        _terms = terms;
        _isLoading = false;
        _loadErrorMessage = terms == null
            ? 'Không tải được điều khoản. Hãy kiểm tra mạng và thử lại.'
            : null;
      });
    }
  }

  Future<void> _handleAccept() async {
    if (!_agreed) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Vui lòng chọn đồng ý với điều khoản sử dụng'),
        ),
      );
      return;
    }

    if (_terms == null) return;
    final termsId = _terms!.id;
    await widget.authController.acceptTerms(termsId);
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.authController;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Điều khoản sử dụng',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        backgroundColor: const Color(0xFF17365D),
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'Đăng xuất',
            onPressed: () => controller.logout(),
          ),
        ],
      ),
      body: RetryableAsyncView(
        isLoading: _isLoading,
        errorMessage: _loadErrorMessage,
        onRetry: _loadErrorMessage == null ? null : _loadTerms,
        child: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: const Color(0xFFF3F7FB),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: const Color(0xFFDCE6F1)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Phiên bản điều khoản: ${_terms?.version ?? "v1.0.0"}',
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                        color: Color(0xFF17365D),
                      ),
                    ),
                    const SizedBox(height: 4),
                    const Text(
                      'Vui lòng đọc và chấp thuận điều khoản dịch vụ trước khi bắt đầu trải nghiệm mạng xã hội LocCoc.',
                      style: TextStyle(fontSize: 13, color: Colors.black87),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Expanded(
                child: Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.grey.shade300),
                  ),
                  child: SingleChildScrollView(
                    child: Text(
                      '1. Mục đích và phạm vi dịch vụ:\n'
                      'LocCoc là ứng dụng mạng xã hội chia sẻ hình ảnh và khoảnh khắc thân mật giữa bạn bè thân thiết.\n\n'
                      '2. Bảo vệ quyền riêng tư & dữ liệu người dùng:\n'
                      'Chúng tôi tôn trọng quyền bảo mật dữ liệu của bạn. Bài đăng và tin nhắn chỉ hiển thị với danh sách bạn bè được bạn chấp thuận hoặc trong kênh hội thoại riêng tư.\n\n'
                      '3. Tiêu chuẩn cộng đồng & Hành vi bị cấm:\n'
                      '- Nghiêm cấm phát tán nội dung bạo lực, phản cảm hoặc vi phạm pháp luật.\n'
                      '- Hành vi quấy rối, xúc phạm thành viên khác sẽ dẫn đến việc đình chỉ hoặc khóa tài khoản vĩnh viễn.\n\n'
                      '4. Quyền của người dùng:\n'
                      'Bạn có toàn quyền chặn (block) người dùng khác, xóa tài khoản hoặc thu hồi dữ liệu cá nhân bất kỳ lúc nào.\n\n'
                      'Chi tiết xem tại: ${_terms?.contentUrl.isNotEmpty == true ? _terms!.contentUrl : "https://loccoc.app/terms"}',
                      style: const TextStyle(
                        fontSize: 14,
                        height: 1.5,
                        color: Colors.black87,
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 16),
              CheckboxListTile(
                value: _agreed,
                onChanged: (val) => setState(() => _agreed = val ?? false),
                title: const Text(
                  'Tôi đã đọc, hiểu rõ và đồng ý với các điều khoản dịch vụ của LocCoc',
                  style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
                ),
                controlAffinity: ListTileControlAffinity.leading,
                contentPadding: EdgeInsets.zero,
              ),
              const SizedBox(height: 12),
              FilledButton(
                onPressed: controller.isLoading || _terms == null || !_agreed
                    ? null
                    : _handleAccept,
                style: FilledButton.styleFrom(
                  backgroundColor: const Color(0xFF17365D),
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: controller.isLoading
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Text(
                        'Đồng ý & Tiếp tục',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
