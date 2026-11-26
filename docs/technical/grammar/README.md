# Grammar Learning

Tài liệu hiện hành cho quản trị học liệu và luồng học viên; module trong code vẫn là `grammar`.

| Cần làm | Tài liệu |
| --- | --- |
| Hiểu storage, API và quy tắc học | [Runtime V2](grammar-v2-runtime.md) |
| Nạp hoặc chuyển dữ liệu cũ | [Import / migration](grammar-v2-importer.md) |
| Dùng đúng level hồ sơ | [Tích hợp User / Placement](grammar-placement-level-integration.md) |
| Chạy BE/FE theo cấu hình nhóm | [Hướng dẫn chạy chung hai module](../placement/placement-local-runtime.md) |
| Nhận bộ học liệu | [README seed](../../../tools/mongo-json-importer/samples/grammar/README.md) |
| Lộ trình và nguồn học thuật | [Lộ trình A1–B2](grammar-cefr-roadmap.md), [nguồn nền](grammar-curriculum-sources.md), [nguồn bổ sung](grammar-coverage-sources.md) |

Ba collection nghiệp vụ là `grammar_topics`, `grammar_lessons`, `grammar_lesson_progress`. Các collection phiên, revision, unlock, counter, lock và audit vẫn phục vụ tính nhất quán; không xóa chúng như file tạm.

Quản trị yêu cầu CONTENT_MANAGER, ADMIN kế thừa; học viên dùng quyền USER kế thừa theo [quy tắc RBAC](../../architecture/security-authorization.md). Luồng học chỉ đọc bản Published. Không đổi User profile khi hoàn thành Grammar, không tự tích hợp đánh giá định kỳ.

Kế hoạch triển khai và báo cáo theo ngày được lưu trong Git history và bản local, không bàn giao như hướng dẫn hiện hành. Trạng thái xuất bản/progress của DB từng máy không phải trạng thái mặc định của seed.

Trạng thái demo dùng chung trên máy thành viên: [setup qua Admin API](../../../tools/mongo-json-importer/README.md#shared-placementgrammar-demo-state), có dry-run, kiểm tra nội dung và giữ lịch sử/tiến độ.
