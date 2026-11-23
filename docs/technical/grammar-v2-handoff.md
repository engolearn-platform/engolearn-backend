# Bàn giao tích hợp Grammar V2 — 20/11/2026

> Cập nhật 20/11/2026: đã nạp đủ lộ trình 32 chủ đề/65 bài/325 câu A1–B2 vào DB dev, tất cả DRAFT. Bộ chuẩn và hướng dẫn hiện tại ở [kết quả nạp toàn bộ](grammar-curriculum-load-result.md) và [README dữ liệu](../../tools/mongo-json-importer/samples/grammar/README.md). Các số liệu/phạm vi một mẫu A1 bên dưới là hồ sơ giai đoạn trước, không dùng để import chồng.


> Cập nhật: mẫu A1 đã được nạp vào DB dev sau khi chủ dự án cho phép, đã xác nhận trên Admin (3 bài/15 câu), vẫn DRAFT. Xem [kết quả nạp A1](grammar-a1-load-result.md). Trạng thái chưa migrate/import bên dưới là hồ sơ trước lượt nạp này.

## Kết quả

Code tích hợp nằm trong backend chính ở `D:/TrenLop/WWWJava/Nhom/EngoLearnApp/engolearn-backend`, nhánh `merge/placement-guest-user-main`. Không tạo backend riêng, không push/merge main. Kiểm thử HTTP chạy từ `target/classes` của chính backend và Vite proxy của frontend chính với cổng/DB tạm; không có JAR riêng để người dùng phải chạy.

Topic V2 lưu Lesson ở grammar_lessons. HTTP API giữ nguyên nên FE không cần đổi source. Legacy tiếp tục dùng Lesson nhúng đến khi người dùng chủ động migrate. Topic mới do Admin tạo dùng V2. Progress dùng grammar_lesson_progress; các collection kỹ thuật hiện có được giữ.

## File source thay đổi trong đợt V2 này

Các đường dẫn dưới đây tính từ backend root:

| File | Thay đổi |
| --- | --- |
| `src/main/java/fit/iuh/engolearn/grammar/model/GrammarTopic.java` | Thêm schema marker và cờ transient để phân biệt aggregate đủ child với projection. |
| `src/main/java/fit/iuh/engolearn/grammar/model/GrammarLessonDocument.java` | Root document mới cho grammar_lessons, giữ draft/published/version/lifecycle. |
| `src/main/java/fit/iuh/engolearn/grammar/mapper/GrammarLessonStorageMapper.java` | Ánh xạ aggregate Lesson sang root document riêng. |
| `src/main/java/fit/iuh/engolearn/grammar/repository/GrammarTopicStorageRepository.java` | Join đúng schema, ghi parent CAS + child trong transaction, bảo vệ projection và dọn child thuộc Topic khi xóa. |
| `src/main/java/fit/iuh/engolearn/grammar/repository/MongoGrammarAdminRepository.java` | Dùng storage mới cho danh sách/đếm/đọc/ghi/xóa. |
| `src/main/java/fit/iuh/engolearn/grammar/repository/MongoGrammarLearnerRepository.java` | Đọc bản xuất bản qua storage mới; fence vẫn ghi cùng parent với Admin. |
| `src/test/java/fit/iuh/engolearn/grammar/repository/GrammarV2PersistenceIntegrationTest.java` | 9 kiểm thử với MongoDB replica container và chính dữ liệu A1. |

Các thay đổi application.yaml/ArchitectureTest và các file FE từ những đợt trước đã có sẵn; đợt này không chỉnh chúng. Không dọn code module khác. Hai đường đọc/ghi legacy và V2 là tương thích chuyển đổi có chủ đích, không phải code chết để xóa trước khi dữ liệu được migrate.

## Tool, dữ liệu và hướng dẫn

- `tools/mongo-json-importer/grammar-v2-migrate.ps1`: preflight mặc định chỉ đọc; Apply riêng, có xác nhận MIGRATE và đối chiếu kế hoạch.
- `tools/mongo-json-importer/grammar-v2-migrate.mongosh.js`: kiểm tra source/code/counter/order/ID/xung đột; chuyển từng Topic trong transaction, giữ liên kết, tăng token parent, không reset tiến độ.
- `tools/mongo-json-importer/samples/grammar/grammar-a1-present-simple.v2.importer.json`: 1 Topic, 3 Lesson riêng, 15 khối/15 câu; tiến độ rỗng.
- `tools/mongo-json-importer/samples/grammar/grammar-v2-counter-bootstrap.importer.json`: counter kỹ thuật chỉ cho namespace Grammar dev rỗng; không thay counter đã có.
- [Hướng dẫn chạy và nạp](grammar-v2-importer.md), [thiết kế runtime](grammar-v2-runtime.md); README của thư mục mẫu đã phân biệt V2 và legacy. Gói legacy giữ lại để hỗ trợ chuyển đổi/kiểm thử, không nạp chồng với V2.

## Kiểm chứng đã chạy

| Kiểm tra | Kết quả |
| --- | --- |
| TDD baseline trước implementation | 8 kiểm thử thất bại đúng ở việc BE chưa hydrate Lesson V2. |
| Regression archive/restore trước sửa null ordering | Phát hiện order chưa về null; đã sửa và kiểm tra lại. |
| GrammarV2PersistenceIntegrationTest | 9/9 đạt: hydration, version/CAS, rollback, mixed legacy/V2, projection, xóa, learner fence, nullable order và Topic mới. |
| GrammarChecklistValidatorTest | 3/3 đạt. |
| ArchitectureTest | 9/9 đạt. |
| FE Vite proxy → BE thật → MongoDB replica tạm | Đạt: generic importer, migration preflight không ghi + Apply + rerun, từ chối mã Lesson trùng, hai bài sửa đồng thời, Admin ready/publish, learner 5/5 và gửi trùng, revision ghim qua update/archive, restore/republish và T0002. |
| Migration PowerShell / JavaScript syntax | Hợp lệ. |
| Preflight DB dev đang dùng | 1 Topic legacy `0fe8ce08-ddec-4e2f-bff5-74883627be5f`, 1 Lesson, sourceVersion=1; migrated=0, writesPerformed=false. |
| `mvnw.cmd verify` toàn BE | **Chưa đạt:** 725 ca; 7 failures, 48 errors, 32 skipped. Không có lỗi ở Grammar/Architecture trong lượt này. |

Các ca lỗi toàn BE nằm ở EngolearnApplicationTests, Subscription (AdminConfiguration/PurchaseFlow/Security) và Topic (learning/auth/browse/quiz). Các nhóm lỗi chính gồm boundary xác thực 401, điều kiện cấu hình/context và các kỳ vọng nghiệp vụ ngoài Grammar. Đợt này không sửa các module đó và không tuyên bố toàn app đã hết lỗi. Không dùng kết quả kiểm thử repository hoặc các bài mocked cũ để thay bằng chứng tích hợp; luồng tích hợp nêu trên gọi API thật qua Vite và DB tạm.

Chưa kiểm tra lại toàn bộ hình ảnh UI bằng browser; không có thay đổi giao diện trong đợt này. Không thể suy rằng DB đang chạy đã có ba bài mẫu chỉ từ các kết quả kiểm thử.

## Trạng thái DB và bước tiếp theo

DB đang dùng **chưa được migrate hoặc import**. Preflight hiện tại chỉ thấy Topic cũ với một Lesson; mẫu A1 ba bài hoàn chỉnh mới dùng ở môi trường kiểm thử và file bàn giao. Việc migrate Topic cũ chỉ tách dữ liệu hiện có, không tự thêm hai bài hoặc ghi đè thành nội dung mẫu.

Người dùng vẫn Run EngolearnApplication và `npm run dev` như thường lệ. Sau khi duyệt, chọn cách đưa mẫu vào DB đích phù hợp và sao lưu trước Apply/import; không nhập gói V2 chồng vào Topic legacy cùng ID/code. A2/B1/B2 mới có lộ trình, chưa có bộ dữ liệu chi tiết và chưa được nạp.

