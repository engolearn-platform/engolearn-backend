# Placement Test

Tài liệu hiện hành cho Guest, học viên và quản trị. Hướng dẫn chung của nhóm vẫn là nguồn chuẩn cho cấu trúc, phân quyền và runtime.

| Cần làm | Tài liệu |
| --- | --- |
| Chạy BE/FE và kiểm tra demo | [Chạy dự án](placement-local-runtime.md) |
| Nạp ngân hàng, bộ đề và media | [Gói dữ liệu dùng chung](../../../tools/mongo-json-importer/samples/placement/README.md) |
| Bắt đầu / tiếp tục lượt làm | [Luồng người học](placement-user-resume.md) |
| Kết quả và cập nhật hồ sơ | [Tích hợp User](placement-profile-level-integration.md) |
| Biên soạn câu hỏi và Listening | [Ngân hàng câu hỏi](placement-admin-question-bank.md) |
| Quản lý vòng đời đề | [Bộ đề](placement-admin-test-sets.md) |
| Tra cứu chỉ đọc | [Kết quả quản trị](placement-admin-attempt-api.md) |

Placement dùng collection `placement_questions`, `placement_test_sets`, `placement_attempts`; media dùng hợp đồng Media của nhóm. Mongo không được đưa lên Cloudinary: Cloudinary chứa file âm thanh, Mongo chứa nội dung, metadata và kết quả. Merge code không tự sao chép DB của người phát triển.

Kiểm tra quyền theo [RBAC](../../runbooks/rbac-verification.md), cookie theo [Auth](../../runbooks/user-auth.md), media theo [hướng dẫn tích hợp](../media/integration-guide.md). Không tạo tài khoản/quyền hay reset DB ngầm khi chạy app.

Các báo cáo triển khai, kết quả kiểm tra theo ngày và kế hoạch cũ không phải hợp đồng runtime. Bản trước được giữ trong Git history và bản lưu local của người phụ trách; không dùng số liệu kiểm thử cũ để kết luận trạng thái hiện tại.
