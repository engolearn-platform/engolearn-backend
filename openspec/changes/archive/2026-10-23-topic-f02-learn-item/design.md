## Context

Hiện tại feature `topic` (`fit.iuh.engolearn.topic`) chỉ hỗ trợ F01 (duyệt danh sách topic và xem chi tiết topic, chỉ đọc, không auth). F02 (học một topic_item) chưa có code. Các collection content (`topics`, `topic_versions`, `topic_items`, `topic_item_versions`) đã có model + repository. Chưa có collection progress, chưa có service/controller cho học item (UC03).

Nguồn đơn sûr: `topic-learning-schema-v1.json` + `topic_learning_data_model.md`. Khi mismatch với code hiện tại, follow chặt file .json. Tất cả ID trong Java class giữ kiểu `String`; trường ID tham chiếu khai báo `@Field(targetType = OBJECT_ID)` để Spring Data lưu dưới dạng `ObjectId` trong MongoDB.

App chưa có Spring Security. Cần một abstraction để lấy `user_id` hiện tại, sau này dễ thay bằng `AuthenticationPrincipal`.

## Goals / Non-Goals

**Goals:**
- Người học đã đăng nhập có thể đọc toàn bộ nội dung item (Bối cảnh, Từ vựng, Mẫu câu) để đi qua bước 1–3 UC03.
- Bấm Tiếp tục khỏi Bối cảnh đánh dấu item `IN_PROGRESS` (BR-03.4), lưu vị trí học dở (part + index).
- Tiếp tục đúng vị trí sau khi thoát giữa chừng (BR-03.1, BR-03.5, BR-03.6).
- Audio không tự phát (BR-F2.7); Kiểm tra nhanh tuỳ chọn, không tính độ chính xác (BR-F2.8).
- 4 collection progress mới: `user_topic_progress`, `user_topic_item_progress`, `user_topic_attempts`, `user_topic_item_attempts`.

**Non-Goals:**
- UC05 Luyện nói (ngoài MVP, đã hoãn).
- UC06 Chuỗi quiz (bài tập, giải thích sau quiz) — thuộc feature F03, không nằm trong UC03.
- Thay đổi API hiện có của `topic-browse` (public chỉ đọc được bảo toàn).
- Thêm Maven module, top-level feature package, hoặc dependency mới.
- BR-03.3 (giữ kết quả quiz, tiếp tục đúng quiz) và BR-03.7 (lượt làm lại có vị trí riêng) — thuộc F03/F04, ngoài slice này.
- BR-04.2, BR-04.3, BR-04.4, BR-04.5, BR-04.7 — quy tắc soạn nội dung và validation xuất bản, thuộc công việc publish-validation F09, không nằm trong phạm vi slice đọc này.

## Quyết định

### D-1: Nguồn đơn sûr và kiểu ID

Quyết định: follow chặt `topic-learning-schema-v1.json` khi có mismatch. Giữ ID là `String` trong Java class, trường tham chiếu dùng `@Field(targetType = OBJECT_ID)`.

Lý do: bro yêu cầu giữ ID kiểu String để giảm thiểu refactor class hiện tại; annotation xử lý mapping sang ObjectId trong DB.

Alternatives: đổi toàn bộ sang `ObjectId` trong Java — bị loại vì đòi hỏi refactor rộng và bro đã chốt String.

### D-2: Vị trí progress collections

Quyết định: 4 collection progress nằm trong package `fit.iuh.engolearn.topic` (model + repository), **không** đưa vào `shared`.

Lý do: progress là kiến thức thuộc feature topic, không phải cross-cutting generic. Việc đặt ở `shared` sẽ vi phạm quy tắc dependency (`shared` không được biết về feature).

Alternatives: collection chung ở `shared` — bị loại.

### D-3: CurrentUserProvider

Quyết định: interface `CurrentUserProvider` ở `fit.iuh.engolearn.topic.utils`, method `String getCurrentUserId()`. Implementation mock `MockCurrentUserProvider` trả `user_id` cố định (ví dụ `"user-mock-001"`), FE không gửi metadata kèm. Sau này viết `SecurityCurrentUserProvider` lấy từ `AuthenticationPrincipal`, đánh `@Primary` để thay thế mock.

Lý do: tách abstraction để controller/service không trực tiếp gọi hàm mock, dễ thay sau khi có Security mà không sửa business code.

Alternatives: inject `AuthenticationPrincipal` ngay bây giờ — bị loại vì app chưa có Security.

### D-4: API endpoints

Quyết định:
- `GET /api/v1/topic-items/{itemId}/content` — trả toàn bộ nội dung item để học.
- `GET /api/v1/topic-items/{itemId}/position` — lấy vị trí học dở (part + index + furthest_reached).
- `PUT /api/v1/topic-items/{itemId}/position` — lưu vị trí + đánh dấu IN_PROGRESS (dùng khi bấm Tiếp tục).
- `GET /api/v1/topics/{topicId}/progress` — lấy tiến độ tổng hợp của user trên topic.

Lý do: tách tiến độ khỏi `GET /topics/{topicId}` để giữ endpoint browse public chỉ đọc, không ép auth vào đường dẫn công khai. Mỗi endpoint có ý nghĩa riêng, FE gọi khi cần.

Alternatives: gộp tiến độ vào topic detail — bị loại vì phá vỡ contrato public chỉ đọc hiện tại.

### D-5: Trạng thái và stage

Quyết định: `UserTopicItemProgress.status` ∈ {NOT_STARTED, IN_PROGRESS, COMPLETED}. Stage theo thứ tự: CONTEXT → VOCAB → EXPRESSIONS → FILL_BLANK → SITUATIONAL_CHOICE (theo BR-04.1 và data model §7.5). `furthest_reached` chỉ cập nhật tiến lên, không cập nhật lùi khi xem lại nội dung phía trước.

Lý do: phù hợp với BR-03.1 (vị trí = part + index), BR-F2.4 (bấm Tiếp tục là xong bước), BR-03.5 (lưu theo version).

Alternatives: cho phép cập nhật lùi furthest_reached — bị loại vì vi phạm quy ước tiến độ.

### D-6: Transaction boundary

Quyết định: lưu position + cập nhật item progress + cập nhật attempt phải nằm trong cùng một thao tác logic nguyên tại (transactional). Nếu một phần thất bại, rollback toàn bộ.

Lý do: shared state — nhiều request cùng ghi vị trí của cùng một learner/item, cần tránh inconsistent state. Đây là senior signal (shared state, partial failure).

Alternatives: lưu từng collection riêng lẻ — bị loại vì rủi ro dữ liệu lệch.

### D-7: Audio hai accent cho từ vựng

Quyết định: `TopicVocabularyResponse` có hai trường `audioEn` (Anh-Anh) và `audioUs` (Anh-Mỹ), trên `WordSnapshotVo` tương ứng là `audioEnUrl` và `audioUsUrl`.

Lý do: BR-04.6 yêu cầu mỗi từ vựng có audio Anh-Anh và Anh-Mỹ. Schema v1 chỉ có `pronunciation_audio_url` (một trường), nhưng cần tách ra hai accent để FE phát âm đúng.

Alternatives: giữ một trường `audioUrl` — bị loại vì không đáp ứng BR-04.6.

### D-8: Vị trí lưu sau khi rời Bối cảnh

Quyết định: sau khi bấm Tiếp tục khỏi Bối cảnh, lưu `part = VOCAB`, `index = 0` (bước kế tiếp), không lưu `part = CONTEXT`.

Lý do: BR-03.1 định nghĩa part là "phần đang xem". Sau khi rời Bối cảnh, phần đang xem là Từ vựng. Lưu `CONTEXT` sẽ khiến resume quay lại bước vừa xong, mâu thuẫn ý định "tiếp tục".

Alternatives: lưu `part = CONTEXT` — bị loại vì gây lệch bước khi resume.

## Rủi ro / Trade-offs

[Risk] Viết đồng thời vào cùng vị trí học dở của learner/item → inconsistent `furthest_reached`. → Giả giảm: phương thức service transactional, optimistic locking trên document progress.
[Risk] Mock `CurrentUserProvider` trả user_id cố định không mô phỏng được đa user trong dev. → Giả giảm: sau này thay bằng `SecurityCurrentUserProvider`, mock có thể đọc từ header test nếu cần.
[Risk] Schema v1 và data model mục 6.2 khác về cấu trúc nội dung `TopicItemVersion` (schema v1 phẳng, mục 6.2 lồng). → Giả giảm: giữ VOs hiện tại (gần mục 6.2), chỉ bổ sung field thiếu (`created_by`, `updated_by`…). Không chuyển sang schema v1 phẳng trừ khi bro quyết định ngược lại.
[Risk] `TopicVersion.items[]` embedded (data model mục 4) khác code hiện tại (`item_version_ids[]` flat). → Giả giảm: giữ flat IDs như code hiện tại, không refactor `TopicVersion` trong UC03 slice này.
[Risk] Integration test `TopicLearningControllerIntegrationTest` tạm thời `@Disabled` trước khi archiving change, vì: (a) lớp cơ sở test đăng ký `spring.data.mongodb.uri`, Spring Boot 4.1 đã chuyển sang `spring.mongodb.*` (cũ bị lỗi cấp error) nên app dưới test kết nối mongod cục bộ của developer thay vì container Testcontainers; (b) fixture chưa set `topics.latest_published_version` và đặt id topic-item vào `topic_versions.item_version_ids` thay vì id item-version. Sửa test (sai khóa thuộc tính, fixture, thuộc tính transaction, cổng RANDOM_PORT, phụ thuộc testcontainers chốt 1.20.1) được hoãn sang một thay đổi riêng. Không khẳng định test vượt.

## Kế hoạch di dữ liệu

Không cần migration data (collection mới, chưa có data cũ). Các collection progress tạo lazy khi người học bắt đầu học item đầu tiên; thiếu document được hiểu là `NOT_STARTED`. Khởi động ứng dụng bình thường, Spring Data sẽ tạo collection khi đầu tiên lưu document.

## Câu hỏi còn mở

- Có cần unique partial index cho `(user_id, topic_id, attempt_type)` với `status = IN_PROGRESS` ngay từ slice đầu, hay chỉ kiểm tra ở service? (Khuyên là cả hai: index + service check.)
- `user_id` mock trả giá trị cứng `"user-mock-001"` hay cho phép override qua header test để FE giả lập nhiều user? (Khuyên là header test `X-Mock-User-Id`, fallback về cứng nếu header trống.)
