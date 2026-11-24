# Grammar A1–B2 — dữ liệu dùng chung theo importer của nhóm

Bộ chuẩn hiện tại: `grammar-cefr-a1-b2.v2.importer.json`, cấu trúc `{metadata, collections}`. Có 3 collection chính: grammar_topics (43), grammar_lessons (106), grammar_lesson_progress (0). Tổng 468 khối/530 câu; UUID String ổn định, snake_case, Date/Long theo Extended JSON. Toàn bộ **seed** là DRAFT/ready=false; không chứa progress/session/revision người dùng. BE cần Grammar V2 trên nhánh hiện tại.

Trạng thái demo dùng chung được khai báo ở `metadata.demo_state`:39 chủ đề/99 bài Published,4 chủ đề/7 bài Draft-ready (mỗi cấp một chủ đề). Seed nội dung vẫn DRAFT/ready=false để tương thích DB cũ và bộ kiểm thử; lệnh thiết lập bên dưới chuyển trạng thái qua Admin API, BE tự tạo revision và audit hợp lệ. Không sao chép tiến độ cá nhân. Không Apply lại bộ này lên DB đã có Grammar. [Nguồn từng bài](../../../../docs/technical/grammar/grammar-coverage-sources.md) và [roadmap hiện hành](../../../../docs/technical/grammar/grammar-cefr-roadmap.md).

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

Chạy EngolearnApplication và npm run dev từ hai repo D. Admin chung mở /admin/grammar, lọc cấp, mở chủ đề → bài → thông tin/khối/câu hỏi → preview/checklist. Bài 14/14 vẫn phải qua Sẵn sàng; có thể thực hiện từ Admin hoặc lệnh thiết lập demo đã chốt bên dưới. Không có migration/seed/xuất bản ngầm khi startup hoặc git pull.

Học viên /grammar chỉ thấy nội dung đã xuất bản theo quyền cấp. Tên hiển thị English (Tiếng Việt); hai trường title_en/title_vi vẫn riêng. Present Simple có6 bài trong bộ học liệu; chỉ hiển thị sau khi xuất bản ở DB đích. Bộ seed không cấp tài khoản/quyền, không tạo tiến độ giả.

## File cũ và an toàn tích hợp

grammar-a1-present-simple.importer.json, grammar-a1-present-simple.v2.importer.json và grammar-v2-counter-bootstrap.importer.json là mẫu legacy/hồ sơ chuyển đổi. Không nhập chung: mã T0001 trùng nhưng root ID mẫu khác ID được giữ trong bộ chuẩn. ValidateGrammarSeed.java chỉ kiểm tra mẫu legacy, không xác nhận toàn bộ bộ hiện tại. data_1.json là tài liệu thiết kế, không phải importer. Migration xem [Grammar V2](../../../../docs/technical/grammar/grammar-v2-importer.md); không tự chạy ở startup.

## Giới hạn bàn giao

Seed là gói học liệu, không phải bản sao DB đang chạy và không chứa tiến độ/session người dùng. Thành viên phải dry-run với DB đích, kiểm tra xung đột và dùng luồng Admin hoặc lệnh thiết lập demo trước khi xem học viên. Không suy ra toàn app hoặc mọi ngoại lệ ngữ pháp đã được kiểm tra từ cấu trúc seed. Các mẫu A1 cũ còn là đầu vào kiểm thử compatibility trong repo, không nhập chung vào namespace hiện hành.

## Cùng trạng thái demo trên máy thành viên

Push/pull Git chỉ chuyển file, không tự thay MongoDB. Sau khi BE chạy, dùng [lệnh thiết lập chung](../../README.md#shared-placementgrammar-demo-state) để có39 Topic/99 Lesson Published và4 Topic/7 Lesson Draft-ready giống bộ demo đã chốt. Placement được đối chiếu đồng thời7 Published/9 Draft.

- **Đã import đúng bộ43/106:** không import lại, chạy dry-run rồi Apply bên dưới.
- **DB Grammar mới:** import gói nội dung và counter theo hướng dẫn trên trước, sau đó chạy lệnh thiết lập.
- **Bản A1/32 Topic cũ hơn, dữ liệu đã sửa hoặc khác ID/order:** preflight dừng, cần đối chiếu/tích hợp chênh lệch trước. Không dùng replace/skip hoặc xóa DB chung để vượt kiểm tra.

```powershell
.\tools\mongo-json-importer\setup-placement-grammar-demo.ps1
.\tools\mongo-json-importer\setup-placement-grammar-demo.ps1 -Apply
```

Nhập tài khoản ADMIN hiện có của app. Lệnh mặc định chỉ kiểm tra; -Apply yêu cầu xác nhận DEMO và chỉ ready/publish các bản ghi khớp seed qua API có version/revision. Chạy lại đúng trạng thái thì0 thao tác. Không ghi trực tiếp status trong Mongo, không reset progress/session/unlock, không nhập tài khoản từ máy người phụ trách, không tự áp dụng bản chỉnh sửa chưa xuất bản hoặc hạ nội dung Published về Draft.

Bốn chủ đề giữ Draft-ready: A1 ENG-GRM-T0009, A2 ENG-GRM-T0017, B1 ENG-GRM-T0041, B2 ENG-GRM-T0044. UI hiển thị “Đang hoàn thiện” vì các bài đã Ready; trạng thái lưu vẫn DRAFT. Counts áp dụng cho43 Topic/106 Lesson chuẩn, không xóa nội dung khác do thành viên tự tạo.
