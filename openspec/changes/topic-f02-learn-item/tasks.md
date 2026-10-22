## 1. Mô hình progress và repository

- [x] 1.1 Tạo model `UserTopicProgress` (collection `user_topic_progress`) theo schema v1, ID kiểu `String`, và repository `UserTopicProgressRepository` extends `MongoRepository`. Verify: class compile và Spring Data nhận collection.
- [x] 1.2 Tạo model `UserTopicItemProgress` (collection `user_topic_item_progress`) với trạng thái NOT_STARTED/IN_PROGRESS/COMPLETED, và repository. Verify: class compile, enum status đúng theo spec.
- [x] 1.3 Tạo model `UserTopicAttempt` (collection `user_topic_attempts`) với attempt_type LEARNING/RETRY, scope ITEM/TOPIC, và repository. Verify: class compile, ràng buộc một attempt IN_PROGRESS mỗi user/topic/attempt_type được ghi nhận.
- [x] 1.4 Tạo model `UserTopicItemAttempt` (collection `user_topic_item_attempts`) với `furthest_reached` (stage + item_index) và `quiz_progress[]`, và repository. Verify: class compile, stage theo thứ tự CONTEXT → VOCAB → EXPRESSIONS → FILL_BLANK → SITUATIONAL_CHOICE.

## 2. CurrentUserProvider (mock)

- [x] 2.1 Tạo interface `CurrentUserProvider` ở `fit.iuh.engolearn.topic.utils` với method `String getCurrentUserId()`. Verify: interface compile, được inject qua constructor.
- [x] 2.2 Tạo implementation `MockCurrentUserProvider` trả user_id cố định (`"user-mock-001"`), đăng ký làm bean. Verify: context khởi động thành công, getCurrentUserId() trả giá trị mong đợi.

## 3. DTO và exception

- [x] 3.1 Tạo request/response DTO cho `GET nội dung`, `GET vị trí`, `PUT vị trí`, `GET tiến độ chủ đề` trong `topic.dto.request|response`. Verify: DTO compile, dùng Lombok builder, không lộ entity.
- [x] 3.2 Tạo exception cho các lỗi học item (ví dụ: `TopicItemNotFoundException`, `LearningPreconditionFailedException`) nếu cần, hoặc reuse exception hiện có. Verify: exception compile, controller có thể ném ra.

## 4. Service layer (shared state — senior)

- [x] 4.1 Tạo interface `TopicLearningService` với các method: đọc nội dung, lấy vị trí, lưu vị trí, lấy tiến độ chủ đề. Verify: interface compile, method signature khớp spec.
- [x] 4.2 Implement `TopicLearningServiceImpl` xử lý đọc nội dung item (gộp context + vocabularies + expressions từ `TopicItemVersion`). Verify: service trả đúng nội dung theo spec, test đơn vị pass.
- [x] 4.3 Implement lưu vị trí + đánh dấu IN_PROGRESS trong transaction (tạo/cập nhật attempt, item attempt, item progress). Verify: transactional, cả ba collection cập nhật cùng lúc, rollback khi lỗi.
- [x] 4.4 Implement tiếp tục vị trí (GET vị trí) và kiểm tra điều kiện tiên quyết (đăng nhập, level topic <= level người học, item trước hoàn thành). Verify: trả vị trí đã lưu, từ chối khi thiếu điều kiện tiên quyết.
- [x] 4.5 Implement `GET tiến độ chủ đề` trả trạng thái tổng hợp của user trên chủ đề. Verify: trả tiến độ đúng theo user_id từ CurrentUserProvider.

## 5. Controller

- [x] 5.1 Tạo `TopicLearningController` với 4 endpoint: GET nội dung, GET vị trí, PUT vị trí, GET tiến độ chủ đề. Verify: endpoint compile, trả `ApiResponse` envelope, dùng `CurrentUserProvider` để lấy user_id.
- [x] 5.2 Thêm `@RestControllerAdvice` cho controller nếu cần, trả `ProblemDetail` (RFC 7807). Verify: lỗi trả về định dạng chuẩn, ArchitectureTest không báo vi phạm.

## 6. Test

- [x] 6.1 Viết unit test cho `TopicLearningServiceImpl` với mocked repositories (nội dung, vị trí, tiến độ). Verify: test pass, cobertura các scenario trong spec.
- [x] 6.2 Viết controller test cho 4 endpoint (mock service, verify request/response). Verify: test pass, status code và body đúng.
- [x] 6.3 Chạy `ArchitectureTest` và toàn bộ test suite của module `topic`. Verify: không có vi phạm arquitectura, tất cả test pass.

## 7. Xác minh và hoàn tất

- [x] 7.1 Chạy `openspec validate --change topic-f02-learn-item --strict` để xác nhận các artifact hợp lệ. Verify: validate không báo lỗi.
- [ ] 7.2 Review toàn bộ artifacts (proposal, specs, design, tasks) với bro trước khi chuyển sang implement. Verify: bro approve artifacts.
