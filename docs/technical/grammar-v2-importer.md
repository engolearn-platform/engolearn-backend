# Grammar V2 — dữ liệu, chạy và chuyển đổi

> Cập nhật 20/11/2026: đã nạp đủ lộ trình 32 chủ đề/65 bài/325 câu A1–B2 vào DB dev, tất cả DRAFT. Bộ chuẩn và hướng dẫn hiện tại ở [kết quả nạp toàn bộ](grammar-curriculum-load-result.md) và [README dữ liệu](../../tools/mongo-json-importer/samples/grammar/README.md). Các số liệu/phạm vi một mẫu A1 bên dưới là hồ sơ giai đoạn trước, không dùng để import chồng.


> Cập nhật: mẫu A1 đã được nạp vào DB dev sau khi chủ dự án cho phép, đã xác nhận trên Admin (3 bài/15 câu), vẫn DRAFT. Xem [kết quả nạp A1](grammar-a1-load-result.md). Trạng thái chưa migrate/import bên dưới là hồ sơ trước lượt nạp này.

BE ở nhánh hiện tại đã hỗ trợ Topic/Lesson tách collection. Khởi động lại bằng EngolearnApplication sau khi lấy code; FE vẫn `npm run dev`, HTTP API giữ nguyên. Dữ liệu cũ vẫn đọc/ghi theo legacy đến khi người dùng chủ động migrate. Không có migration/import tự chạy ở startup.

## Gói học liệu

`tools/mongo-json-importer/samples/grammar/grammar-cefr-a1-b2.v2.importer.json` dùng `{metadata,collections}` của nhóm. Ba collection chính: grammar_topics (32 root), grammar_lessons (65 root), grammar_lesson_progress (0 root, không tạo tiến độ giả). Gồm 263 khối và 325 câu; tất cả DRAFT, ready=false, content_version=0. Sáu collection kỹ thuật sẵn có tiếp tục phục vụ phiên/revision/mở khóa/counter/lock/audit.

Tên progress theo V2 và BE là grammar_lesson_progress. Không tạo thêm user_grammar_progress từ data_1 gốc. Bộ đầy đủ giữ ID/nội dung ba bài A1 đã nạp thực tế và thêm 31 chủ đề còn lại. Các gói mẫu A1 cũ có ID nguồn khác; không import chồng với bộ đầy đủ.

## DB đã có Grammar — ưu tiên migration, không import chồng

Từ thư mục backend, preflight mặc định chỉ đọc và dùng cấu hình MongoDB của importer hiện có:

```powershell
.\tools\mongo-json-importer\grammar-v2-migrate.ps1
```

Đọc report: source version/hash, Topic cần chuyển và số bài, Topic đã V2, mã/counter/order và xung đột child. Chỉ sau khi sao lưu, dừng thao tác biên soạn và được phép ghi DB mới dùng:

```powershell
.\tools\mongo-json-importer\grammar-v2-migrate.ps1 -TopicId '<Topic ID từ report>' -Apply
```

Lệnh Apply yêu cầu MIGRATE, đối chiếu lại kế hoạch và hash rồi chuyển từng Topic trong transaction. Giữ ID, code, child version, draft/published, first_published_at, revision/progress/session/audit. Tăng version của parent để trình biên soạn đang giữ token cũ phải tải lại. Không tự sửa counter thấp hoặc dữ liệu xung đột; gặp lỗi thì dừng. Các Topic trước đó đã commit vẫn được giữ nếu một Topic sau lỗi; chạy preflight mới để tiếp tục.

`ALREADY_V2` kiểm tra cấu trúc hiện hành, không chứng minh nội dung vẫn giống bản trước migration vì Admin có thể đã chỉnh sửa hợp lệ. Không replace bản V2 từ snapshot cũ. Nguồn standalone/ObjectId khác với legacy UUID của repo cần mapping riêng, không tự suy đoán chuyển đổi.

## DB dev Grammar mới — import theo tool chung

Chỉ dùng khi grammar_topics, grammar_lessons và grammar_counters đều rỗng, không có tiến độ Grammar cũ cần giữ. DB/cấu hình quyền do nhóm cung cấp; không đổi .env hoặc quyền truy cập ngầm định. Dừng ghi nội dung trong lúc nạp vì generic importer ghi nhiều collection theo batch, không phải một transaction chung.

Gói học liệu không ghi counter. Gói kỹ thuật `grammar-cefr-a1-b2.counter-bootstrap.importer.json` cấp topic_sequence=33 cho namespace rỗng để lần Admin tạo tiếp theo là T0034. Mã có khoảng trống lịch sử; không hạ counter theo tổng số Topic. Nạp nội dung trước, counter sau khi cả hai dry-run đạt và đang dừng biên soạn. Không dùng bootstrap để thay counter đã tồn tại hoặc hạ giá trị counter.

```powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\grammar\grammar-cefr-a1-b2.v2.importer.json -DryRun
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\grammar\grammar-cefr-a1-b2.counter-bootstrap.importer.json -DryRun
```

Sau khi duyệt đúng DB và kết quả preflight, bỏ `-DryRun` để nạp từng gói; giữ policy fail cho lần đầu và xác nhận IMPORT. Không dùng replace. Không nạp đồng thời gói legacy và V2 vì cùng ID/code; các file nằm trong thư mục con nên không được tự chọn bởi lệnh all-samples ở cấp trên.

## Xác nhận trên ứng dụng

Admin vào `/admin/grammar`: Topic nháp có 0/3 bài Sẵn sàng, mỗi bài đủ năm khối/năm câu. Kiểm tra lời giải, bật Sẵn sàng cho từng bài rồi xuất bản bằng luồng thật. Learner chỉ thấy Topic sau khi xuất bản; đọc lý thuyết, gửi seen và làm Practice. Đạt ít nhất 3/5 để mở bài tiếp theo. Nguồn level hồ sơ/Placement giữ OQ hiện hành, không đổi trong việc tách storage.

## Kiểm chứng ngày 20/11/2026

- 9 kiểm thử repository với bộ A1 thật trên MongoDB container tạm: đọc V2/legacy, CAS, rollback, projection guard, xóa child, thống kê mixed, nullable archive/restore và learner fence.
- Luồng thật FE Vite proxy → BE chạy từ target/classes → MongoDB replica tạm: importer chung, migration preflight không ghi, Apply và chạy lại, chống mã Lesson trùng, hai biên tập viên sửa hai bài đồng thời, Admin ready/publish, Learner 5/5 và gửi trùng, giữ revision khi cập nhật/lưu trữ, restore/republish và cấp mã T0002: đạt.
- Đây là kiểm tra HTTP qua proxy, chưa phải kiểm tra trực quan lại toàn bộ màn hình trên browser. FE source không đổi.
- DB dev hiện đã có đủ 32 chủ đề/65 bài sau lượt nạp được duyệt; xem báo cáo mới ở đầu tài liệu. Lượt này không commit/push/merge main. Kết quả toàn BE vẫn phải đọc riêng, không suy từ kiểm tra dữ liệu Grammar.

