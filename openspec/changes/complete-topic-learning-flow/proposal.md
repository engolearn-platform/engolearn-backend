## Why

FE đã đọc catalog và học nội dung qua BE/MongoDB thật, nhưng dừng trước quiz. Cần hoàn tất quiz, kết quả và xem lại/làm lại trên cùng luồng thật; đồng thời thay các luật OpenSpec cũ đã trái quyết định nghiệp vụ.

## What Changes

- Thêm quiz FILL_BLANK rồi SITUATIONAL_CHOICE: BE chấm ngay, lưu lần sai qua reload, đúng mới mở câu tiếp; không lộ đáp án trước khi đúng.
- Hoàn thành ở lần đúng cuối, tổng kết item/topic từ kết quả hiện hành, mở item tiếp theo; retry thay kết quả theo từng item đã xong, không theo điểm tốt nhất.
- Xem lại chỉ đọc và retry ITEM/TOPIC có phiên riêng, version đã chốt, xác nhận thay lượt cũ.
- **BREAKING**: item HIDDEN dừng mọi truy cập learner, kể cả attempt đang chạy; snapshot đã pin không vượt quyền visibility hiện hành.
- Chuẩn hóa EXPRESSIONS index theo mục đích giao tiếp; nav không có cấp nhóm. Giữ dữ liệu group và vị trí hiện có.
- Đồng bộ start tại CONTEXT/0, discover/mine và catalog cá nhân vào delta spec. Legacy item content/position routes vẫn 410, không khôi phục.
- Viết artifact dữ liệu mẫu riêng: script nạp dữ liệu thật, idempotent, namespace riêng, không sửa dữ liệu/progress sẵn có. Phiên planning này không tạo script hay nạp DB.

## Capabilities

### New Capabilities

- `topic-quiz`: câu hỏi hiện tại, gửi đáp án, giải thích, lưu tiến độ và replay.
- `topic-completion`: hoàn thành nhất quán, tổng kết item/topic và xem lại kết quả hiện hành.
- `topic-retry`: retry item/topic, thay thế có xác nhận, đóng băng version và bỏ qua item ẩn.

### Modified Capabilities

- `topic-learn-item`: start ngay khi xác nhận, content theo owned attempt, vị trí theo purpose, quyền với item ẩn.
- `topic-browse`: catalog cá nhân, discover/mine, quyền hành động và kết quả hiện hành sau completion/retry/hide.

## Impact

BE: feature `topic` controller/service/repository/DTO/model/mapper, projection tiến độ và retry session; giữ package boundaries, không thêm dependency hay auth. FE là consumer liên repo: tiếp tục `TopicLessonView`/`useTopicLesson`, nested nav, quiz/result/review/retry; kế hoạch FE riêng tại `engolearn-frontend/docs/features/topic/learner/10-completion-implementation-plan.md`.

Nguồn hành vi: repo business `docs/usecase_spec/topic-learning/00-module.md`, F02/F03/F04; `docs/technical/topic-learning/topic-learning-schema-v1.json`, `topic_learning_data_model.md`, `learner-runtime-mapping.md`. Schema là mô hình tham khảo, không phải migration literal. API mục tiêu hiện ở FE `docs/handout/topic-learning/api.openapi.yaml`; design chỉ rõ phần còn thiếu và delta cần đồng bộ trước code.

Không nằm trong scope: auth, WORD_ORDER/MATCHING/DICTATION, skip câu chưa đúng, XP/huy hiệu, lưu vocab, report issue, thay hạ tầng Mongo, fixture server/mock provider, reset dữ liệu. Dữ liệu mẫu được phép lưu thật qua script do người dùng chạy. Agent/subagent dữ liệu được phép chuẩn bị file, không được tự nạp DB.
