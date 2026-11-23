# Grammar A1–B2 — dữ liệu dùng chung theo importer của nhóm

Bộ chuẩn hiện tại: `grammar-cefr-a1-b2.v2.importer.json`, cấu trúc `{metadata, collections}`. Có 3 collection chính: grammar_topics (43), grammar_lessons (106), grammar_lesson_progress (0). Tổng 468 khối/530 câu; UUID String ổn định, snake_case, Date/Long theo Extended JSON. Toàn bộ **seed** là DRAFT/ready=false; không chứa progress/session/revision người dùng. BE cần Grammar V2 trên nhánh hiện tại.

DB local **đã cập nhật qua API**:39 chủ đề/99 bài Published;4 chủ đề/7 bài Nháp để demo (mỗi cấp A1–B2 một chủ đề). Không Apply seed này lần nữa lên DB local. Xem [kết quả bổ sung](../../../../docs/technical/grammar-coverage-result.md), [nguồn từng bài](../../../../docs/technical/grammar-coverage-sources.md) và [roadmap hiện hành](../../../../docs/technical/grammar-cefr-roadmap.md).

## Thành viên khác nạp vào MongoDB dev

Chạy từ repo BE, bật MongoDB replica set và cấu hình `.env` theo [importer chung](../../README.md). Chỉ nạp vào namespace Grammar trống: chưa có Topic/Lesson/counter/progress/session/revision/unlock Grammar cần giữ. Không yêu cầu các module khác trống và không reset DB nhóm.

1. Kiểm tra DB đích/sao lưu. DB đã có Grammar phải đối chiếu ID/code/order/counter và tích hợp chênh lệch qua Admin, không replace hay dùng skip để che xung đột.
2. Chạy DryRun cả nội dung và counter dưới đây, policy fail/continue-on-error=false. Metadata là ghi chú, không tự chặn nhập nhầm.
3. Chỉ bỏ -DryRun khi đã xác nhận namespace trống; nhập nội dung trước, counter sau. Tool có bước IMPORT. Không dùng replace. Importer không phải transaction liên collection; nếu lỗi giữa chừng phải đối chiếu trước khi tiếp tục.
4. Counter bootstrap chỉ dành namespace mới, sequence snapshot=44; không đặt bằng số Topic (43) vì có khoảng trống mã lịch sử. Không ghi đè counter đang dùng.

```powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\grammar\grammar-cefr-a1-b2.v2.importer.json -DryRun
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\grammar\grammar-cefr-a1-b2.counter-bootstrap.importer.json -DryRun
```

Nội dung gồm 149 root document (43 Topic + 106 Lesson); progress rỗng. Không đổi UUID thành ObjectId. Các file ở samples/grammar để lệnh all-samples cấp trên không tự chọn tất cả; luôn truyền đúng -File.

## Xem Admin và học viên

Chạy EngolearnApplication và npm run dev từ hai repo D. Admin chung mở /admin/grammar, lọc cấp, mở chủ đề → bài → thông tin/khối/câu hỏi → preview/checklist. Bài 14/14 vẫn phải duyệt Sẵn sàng; bài thuộc Topic đã published có bước xác nhận tự động xuất bản. Không có thao tác xuất bản toàn bộ tự động trong bộ seed.

Học viên /grammar chỉ thấy nội dung đã xuất bản theo quyền cấp. Tên hiển thị English (Tiếng Việt); hai trường title_en/title_vi vẫn riêng. Present Simple có6 bài biên soạn đã Published trên local. Bộ seed không cấp tài khoản/quyền, không tạo tiến độ giả.

## File cũ và an toàn tích hợp

grammar-a1-present-simple.importer.json, grammar-a1-present-simple.v2.importer.json và grammar-v2-counter-bootstrap.importer.json là mẫu legacy/hồ sơ chuyển đổi. Không nhập chung: mã T0001 trùng nhưng root ID mẫu khác ID được giữ trong bộ chuẩn. ValidateGrammarSeed.java chỉ kiểm tra mẫu legacy, không xác nhận toàn bộ bộ hiện tại. data_1.json là tài liệu thiết kế, không phải importer. Migration xem [Grammar V2](../../../../docs/technical/grammar-v2-importer.md); không tự chạy ở startup.

## Đã kiểm tra và giới hạn

Đã kiểm tra DTO/checklist/normalizer production cho 41 bài thêm, đọc lại API thật, bảo toàn65 bài cũ/progress/session/revision và xuất seed đúng root/quan hệ/enum/trạng thái/count. Xem báo cáo để biết kết quả static seed/dry-run chi tiết. Chưa Apply toàn bộ bundle vào namespace trống khác;4 chủ đề local giữ Nháp có chủ ý để demo. Trạng thái trong seed vẫn DRAFT/ready=false, thành viên khác phải kiểm tra và xuất bản qua Admin trước khi xem phía học viên. Không suy thành toàn app không lỗi hoặc đã bao phủ mọi ngoại lệ ngữ pháp.
