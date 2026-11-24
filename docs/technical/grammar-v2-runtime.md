> Quy tắc mở cấp cập nhật 23/11/2026: hoàn thành tối thiểu70% chủ đề đủ điều kiện của cấp trước, làm tròn lên bằng số nguyên. Chủ đề phải hoàn thành đủ tất cả bài Published; mỗi bài vẫn3/5. Giữ mọi unlock đã lưu và quyền từ profile; không tự nâng level User. Xem [thiết kế và kiểm chứng](grammar-unlock-70-review.md).

> Cập nhật tích hợp level 23/11/2026: Grammar đọc UserLevelLookup, mở cấp đến level hồ sơ (C1/C2 giới hạn B2), không tự hoàn thành bài hoặc nâng level User. MVP dùng cefr_level chỉ tăng; hai trường latest/max riêng vẫn hoãn. Ghi chú mặc định A1/hoãn toàn bộ tích hợp bên dưới là lịch sử. Xem tài liệu grammar-placement-level-integration.md ở BE và 06-placement-level-integration.md ở FE.

> Số liệu hiện hành sau bổ sung23/11/2026: xem [kết quả coverage](grammar-coverage-result.md); các số liệu của đợt triển khai bên dưới là hồ sơ lịch sử.

# Grammar V2 — persistence và chuyển đổi

> User UI v4 (22/11/2026): learner feedback trả thêm `type` và `prompt` cho câu đã nộp, lấy từ revision của session; giúp xem lại result sau reload. Không đổi schema/collection. Xem `docs/technical/grammar-user-v4-result.md`.


> Cập nhật 20/11/2026: đã nạp đủ lộ trình 32 chủ đề/65 bài/325 câu A1–B2 vào DB dev, tất cả DRAFT. Bộ chuẩn và hướng dẫn hiện tại ở [kết quả nạp toàn bộ](grammar-curriculum-load-result.md) và [README dữ liệu](../../tools/mongo-json-importer/samples/grammar/README.md). Các số liệu/phạm vi một mẫu A1 bên dưới là hồ sơ giai đoạn trước, không dùng để import chồng.


> Cập nhật: mẫu A1 đã được nạp vào DB dev sau khi chủ dự án cho phép, đã xác nhận trên Admin (3 bài/15 câu), vẫn DRAFT. Xem [kết quả nạp A1](grammar-a1-load-result.md). Trạng thái chưa migrate/import bên dưới là hồ sơ trước lượt nạp này.

Phạm vi được chủ dự án duyệt ngày 20/11/2026: triển khai bộ model V2 đã chọn, giữ HTTP API và FE. Ba collection nghiệp vụ chính là grammar_topics, grammar_lessons, grammar_lesson_progress; giữ các collection kỹ thuật sẵn có cho phiên, revision, mở khóa, counter, lock và audit.

## Quy tắc storage

- Topic không có schema_version hoặc bằng 1: đọc/ghi Lesson nhúng như cũ. Không tự migrate khi startup hoặc khi người học đọc bài.
- Topic schema_version=2: nội dung Lesson nằm ở grammar_lessons; `_id` của Lesson giữ nguyên ID cũ, `topic_id` trỏ Topic. Topic không lưu mảng lessons. Aggregate trong bộ nhớ vẫn có danh sách Lesson để tái sử dụng service/DTO hiện tại.
- Topic mới do Admin tạo dùng V2. Mọi lần thêm/sửa/xóa child V2 đều ghi parent bằng version CAS trong cùng transaction. Version Topic và child là hai edit token riêng; learner fence ghi cùng parent nhưng không thay edit token.
- Query danh sách/lọc/đếm và learner dùng lookup theo đúng schema của từng Topic. Projection không được đem save, tránh mất child khi dữ liệu chưa tải đầy đủ. Không gửi đáp án chưa nộp qua learner API.
- Mọi ghi V2 yêu cầu transaction MongoDB replica set. Lỗi child, CAS hoặc xung đột ID phải rollback parent và tất cả child. Xóa cứng Topic nháp xóa child cùng transaction. Chỉ xóa child thuộc đúng Topic.
- Progress tiếp tục ở grammar_lesson_progress như BE hiện tại. Không tạo thêm user_grammar_progress hoặc đổi ID/user/tiến độ ngoài phạm vi.

## Model/giới hạn

Giữ UUID/String, code ENG-GRM-Txxxx/Lxxx, snake_case, draft/published, ready và content_version. Topic nháp có thể chưa có bài; bài nháp có thể chưa đủ nội dung. Sẵn sàng yêu cầu checklist 14 mục, mỗi bài 1–7 khối và đúng 5 câu. Xuất bản Topic yêu cầu 1–10 bài đạt kiểm tra. Tỷ lệ loại câu không bị cố định bởi storage.

## Migration

Công cụ chuyển đổi phải mặc định preflight chỉ đọc; Apply phải là lựa chọn rõ ràng. Kiểm tra DB dev, Topic/version/ID/code/order/counter, xung đột với child V2, rồi chép nguyên nội dung vào child và đổi marker trong cùng transaction cho từng Topic. Không chạy migration trong startup hoặc service nghiệp vụ. Không đặt lại progress/session/revision/audit. Không tự biến một nguồn standalone/ObjectId khác thành legacy UUID.

## Kiểm chứng

Kiểm thử dùng chính bundle A1 V2 và bundle legacy đã chuẩn bị, trên MongoDB replica set container tạm riêng. Không dùng mocked API để kết luận tích hợp. Phải kiểm tra hydration 3 bài, ghi child/CAS, rollback, projection guard, xóa child/Topic, thống kê mixed legacy/V2 và fence khi learner đọc bản đã xuất bản. Kiểm tra HTTP/FE và migration được báo riêng, không suy từ kiểm thử repository.

DB đang dùng của chủ dự án chưa được migrate/import. Không push/merge main.


## Team dev integration — 23/11/2026

Current authorization, dev/prod runtime, Cloudinary migration, local port handling and actual verification are maintained in [Placement/Grammar integration](placement-grammar-dev-integration.md). This supersedes older module-profile commands and the historical exact-USER-only learning activation restriction.

## Structure standardization — 23/11/2026

Grammar stays inside the existing feature. FE components/views use `learning/` and `management/`; hooks/services/types remain feature-owned. Management route exports are `GrammarManagementRoutes`; browser URLs and API URLs retain `/admin/grammar` and `/api/v1/grammar/admin/topics`. Reusable content types live in `types/grammar-content.types.ts`.

BE web records are individual files in `grammar/dto/request` and `grammar/dto/response`, with Request/Response suffixes. Management content requests and responses are mapped explicitly in GrammarAdminMapper. The consolidated GrammarAdminDtos/GrammarLearnerRequests/GrammarLearnerResponses classes were removed; the seed validator imports the replacement records. JSON fields, validation, roles and business rules are preserved. No persistence model, collection, ID, seed content or learner progress is migrated by this change.

Chi tiết thay đổi cấu trúc và kết quả kiểm chứng: [báo cáo chuẩn hóa](grammar-structure-standardization.md).

## UX metadata và tìm kiếm24/11/2026

GrammarLessonSummaryResponse thêm blockCount, đếm content.getBlocks của bản Published đã hydrate cùng topic. Thay đổi JSON additive, không đổi collection/ID hoặc tiến độ. MongoGrammarAdminRepository tìm literal không dấu/hoa thường bằng character class tiếng Việt và escape ký tự regex; query giữ count/filter/paging/sort/export hiện có. Không thêm fuzzy index hoặc sửa lỗi chính tả. Test GrammarV2PersistenceIntegrationTest.accentInsensitiveLiteralSearchUsesRealV2Data dùng seed V2 thật trên Mongo replica set riêng, kiểm tra không dấu/có dấu/CEFR và .*.
