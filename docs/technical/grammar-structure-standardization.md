# Chuẩn hóa cấu trúc Grammar — 23/11/2026

Thực hiện tại hai repo `D:/TrenLop/WWWJava/Nhom/EngoLearnApp`, trên các nhánh có sẵn: BE `merge/placement-guest-user-main`, FE `fix/placement-guest-navbar`. Không tạo nhánh mới, không push, không merge vào main/dev.

## Phạm vi

| Phần | Trước | Hiện tại |
| --- | --- | --- |
| FE màn quản trị | components/admin, views/admin | components/management, views/management |
| FE màn học | components và views gốc | components/learning, views/learning |
| FE route quản trị | admin.routes.ts, GrammarAdminRoutes | management.routes.ts, GrammarManagementRoutes |
| FE client/guard | grammarAdmin, GrammarAdminGuard | grammarManagement, GrammarManagementGuard |
| FE kiểu nội dung dùng chung | grammar-admin.types.ts | grammar-content.types.ts |
| BE HTTP DTO | 3 lớp tổng hợp ở dto gốc | 34 record riêng ở dto/request và dto/response |

BE có 31 record chuyển từ lớp tổng hợp và 3 record response nội dung mới để response không phụ thuộc request. GrammarAdminMapper chuyển đổi rõ ràng giữa hai phía. Đã cập nhật controller, service, mapper, exception, kiểm thử và công cụ ValidateGrammarSeed. FE cập nhật hooks, imports và router của app; router chỉ đổi tên import/export, giữ nguyên cây URL và quyền.

Giữ nguyên `/grammar`, `/admin/grammar`, `/api/v1/grammar/admin/topics`, các trường JSON, validation, roles và nghiệp vụ. Không đổi model MongoDB, collection, ID, học liệu, trạng thái xuất bản hay tiến độ. Không thay logic Placement hoặc module khác. Ghi chú lỗi profile IDE trước đó được giữ lại.

## Kiểm chứng thực tế

- FE `npm run build`: đạt; cảnh báo kích thước bundle vẫn còn.
- FE `npm run lint`: 0 lỗi, 72 cảnh báo hiện có.
- FE `npm run test:auth`: 21/21 đạt; đây là kiểm tra bổ sung của nhóm, không thay thế tích hợp thật.
- BE clean test-compile: đạt. Build sạch loại bỏ class DTO cũ.
- BE `mvnw.cmd verify` dùng database kiểm thử tạm, không dùng DB nghiệp vụ: 872 tests; 12 failures, 49 errors, 32 skipped. Grammar 12/12 đạt; Placement 166 tests, 0 failure/error, 4 skipped; ArchitectureTest 9/9 đạt. Tất cả 61 failure/error nằm ngoài Grammar/Placement. Không khẳng định toàn dự án đã xanh.
- API thật: so sánh toàn bộ danh sách/chi tiết quản trị của 43 chủ đề, 106 bài, roadmap, chủ đề học viên, lý thuyết và checklist qua FE proxy localhost:3000 với BE mới chạy từ classes trên cổng tạm 8082: JSON bằng nhau. Đã dừng BE kiểm tra riêng, giữ nguyên tiến trình của người dùng.
- ValidateGrammarSeed với file mẫu legacy: DTO/checklist 3 bài đạt 14/14; chỉ đọc file, không truy cập DB. Kiểm tra này xác nhận công cụ legacy sau đổi import, không khẳng định đã nhập lại hoặc kiểm tra lại toàn bộ bộ học liệu hiện tại.
- Review độc lập: không phát hiện lỗi cụ thể về JSON/validation/import/mapping trong phạm vi đổi cấu trúc.

## Chạy tiếp

Run `EngolearnApplication` với duy nhất profile `dev`; FE `npm run dev`. URL và tài khoản hiện có không đổi. Nếu IDE còn giữ class cũ thì stop/run lại BE; không chạy thêm một BE khác chiếm cổng 8080. Không cần import lại data vì lần này chỉ thay cấu trúc source.
