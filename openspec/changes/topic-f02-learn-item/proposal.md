## Tại sao

Người học chưa có cách bắt đầu học nội dung chi tiết của một topic_item (Bối cảnh → Từ vựng → Mẫu câu), lưu vị trí học dở và tiếp tục từ nơi đã dừng. Hiện tại feature `topic` chỉ hỗ trợ duyệt danh sách và xem chi tiết topic (F01, chỉ đọc, không auth). Cần bổ sung UC03 để người học đăng nhập có thể bắt đầu học một item, đánh dấu item là đang học dở khi bấm Tiếp tục khỏi bước Bối cảnh, và tiếp tục đúng vị trí sau khi thoát giữa chừng.

## Những thay đổi

- Thêm 4 collection progress mới: `user_topic_progress`, `user_topic_item_progress`, `user_topic_attempts`, `user_topic_item_attempts` (theo `topic_learning_data_model.md` mục 7 và schema v1).
- Thêm model và repository cho 4 collection progress trong package `fit.iuh.engolearn.topic`.
- Thêm service `TopicLearningService` + impl xử lý: đọc nội dung item để học, lưu vị trí (part + index), đánh dấu đang học dở, tiếp tục vị trí.
- Thêm controller `TopicLearningController` với các endpoint: GET nội dung, GET vị trí, PUT vị trí, GET tiến độ chủ đề.
- Thêm `CurrentUserProvider` ở package `utils` trong feature `topic` (mock trả user_id cố định, sau này thay bằng Spring Security principal).
- Các class hiện tại (`Topic`, `TopicVersion`, `TopicItem`, `TopicItemVersion`, VOs) tuân chặt `topic-learning-schema-v1.json` khi có mismatch; giữ ID là kiểu `String` trong Java, trường ID tham chiếu dùng `@Field(targetType = OBJECT_ID)`.

## Khả năng

### Khả năng mới

- `topic-learn-item`: Học nội dung một topic_item (UC03): đọc nội dung item, bắt đầu/tiếp tục học, lưu vị trí học dở, đánh dấu item đang học dở, tiếp tục từ vị trí đã dừng. Đánh step 1-3 (Bối cảnh → Từ vựng → Mẫu câu), bàn giao sang chuỗi quiz UC06.

### Khả năng đã sửa

- (không có)

## Tác động

- Code mới: package `fit.iuh.engolearn.topic` (model, repository, service, controller, dto, exception, utils).
- API mới: `GET /api/v1/topic-items/{itemId}/content`, `GET /api/v1/topic-items/{itemId}/position`, `PUT /api/v1/topic-items/{itemId}/position`, `GET /api/v1/topics/{topicId}/progress`.
- Không đổi API hiện có của `topic-browse` (public chỉ đọc được bảo toàn).
- Dependency: không thêm Maven module, không thêm top-level feature package. Sử dụng package `topic` hiện có (đã là approved feature root).
- Auth: hiện tại dùng `CurrentUserProvider` mock, sau này thay bằng Spring Security principal khi có Spring Security.
