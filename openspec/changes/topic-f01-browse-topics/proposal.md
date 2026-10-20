## Why

Người học hiện chưa có cách nào khám phá nội dung: repo mới có model, DTO và contract, nhưng **không có controller, không có repository, không có service impl** — không endpoint nào chạy được. F01 (UC01 duyệt danh sách topic, UC02 xem chi tiết topic) là cửa vào của toàn bộ module Học theo chủ đề, và là feature duy nhất có đặc tả đầy đủ trong material hiện có.

Làm F01 trước để có lát cắt dọc đầu tiên chạy được, đồng thời ép chốt hình dạng lưu trữ: entity hiện tại đang lệch `material/topic-learning-schema-v1.json`, và để yên thì mọi feature sau sẽ nhân lên độ lệch đó.

## What Changes

- Thêm đường đọc cho người học: danh sách topic đã publish (lọc theo level, tìm theo tên, sắp xếp) và chi tiết một topic kèm danh sách item đang hiển thị theo thứ tự.
- Thêm phân trang dạng cuộn cho danh sách topic: mặc định 20 topic mỗi trang, phản hồi kèm con trỏ cho trang kế tiếp và dấu còn trang hay không; `limit` tuỳ chọn với trần 100.
- Chốt quy ước API: danh sách ở `/api/v1/topics`, chi tiết ở `/api/v1/topics/{topicId}`, field JSON dùng camelCase.
- Căn entity theo `material/topic-learning-schema-v1.json`:
  - `topic_items` chỉ còn **định danh** (mất toàn bộ nội dung).
  - Nội dung chuyển sang collection mới `topic_item_versions` (context, vocabularies, expressions, quizzes, order, status, version).
  - `topic_versions` mang manifest `item_version_ids` để xác định tập item của một release.
- Thêm `duration_minutes` vào topic item version: schema v1 thiếu field này nhưng UC02 yêu cầu hiển thị "Thời lượng ước tính" cho từng item.
- Đổi con trỏ release trên `Topic` thành tham chiếu tới topic version (thay vì số nguyên); trạng thái "topic đã publish" suy ra từ con trỏ này, vì schema v1 không có field status cho topic.
- Thêm `HIDDEN` vào tập trạng thái nội dung để biểu diễn item bị ẩn; item ẩn không hiển thị, không tính vào số đếm và thời lượng.
- Thêm repository, service và controller cho F01, kèm response DTO khớp bảng hiển thị của UC01/UC02 (tên, level, ảnh, số từ vựng, số bài tập, thời lượng).
- Thêm seed data cho môi trường dev để có dữ liệu chạy và smoke test HTTP.
- **BREAKING**: nội dung item rời khỏi `topic_items`, và con trỏ release trên topic đổi kiểu. Document cũ trong DB không còn đọc được — phải seed lại. Chấp nhận được vì chưa có dữ liệu thật.

**Non-goals (ngoài scope lần này):**

- Tiến độ người học trên danh sách/chi tiết topic, badge "chưa phù hợp", badge "có nội dung mới".
- Bộ lọc theo trạng thái học và theo nội dung mới (phụ thuộc tiến độ).
- Luồng học một topic_item (F02), làm quiz (F03), hoàn thành và làm lại (F04).
- Soạn nội dung, lưu nháp, publish, MINOR/MAJOR (F05–F10).
- Auth/principal, phân biệt khách chưa đăng nhập, nguồn level người học.

## Capabilities

### New Capabilities

- `topic-browse`: người học duyệt danh sách topic đã publish và xem chi tiết một topic. Bao gồm: chỉ topic đã publish mới hiển thị; item bị ẩn không hiển thị và không tính vào số đếm/thời lượng; thứ tự item là thứ tự cố định của topic; số từ vựng và số bài tập là giá trị hệ thống đếm; thời lượng topic là tổng thời lượng các item đang hiển thị; danh sách trả theo trang cuộn với mặc định 20 topic mỗi trang.

### Modified Capabilities

Không có. `openspec/specs/` hiện đang trống nên không có requirement nào bị thay đổi.

## Impact

**Code**

- `topic`: `model` (Topic, TopicVersion, TopicItem, **thêm TopicItemVersion**), `model.enums` (ContentStatus), `model.vo`, `dto.request`, `dto.response`, `service` (impl cho `TopicBrowseService`), `repository` (mới), `controller` (mới).
- `config`: thêm seed runner chỉ chạy ở profile dev.
- `dictionary`, `user`: **không đổi**.

**Kiến trúc**

- Không thêm phụ thuộc cross-feature. Bảng phụ thuộc đã duyệt (`dictionary <- topic <- user`) giữ nguyên.
- Không thêm Maven dependency, module, hay top-level feature package.
- `ArchitectureTest` phải tiếp tục xanh.

**Rủi ro và giới hạn đã biết (phải khai báo, không được giấu)**

1. Không có MongoDB, không có testcontainers, không có embedded Mongo trong môi trường hiện tại → **tính đúng của query MongoDB không verify được bằng test tự động**. Chỉ verify được logic service bằng mock, cộng với ArchitectureTest và `mvnw verify`.
2. Smoke test HTTP cần Docker Desktop đang chạy và `docker-compose up -d`; hiện Docker đang tắt nên đây là bước thủ công.
3. `rules.md` của f01 thiếu BR-01.4, BR-01.5, BR-02.5, BR-02.6 trong khi UC01/UC02 đang tham chiếu chúng. Không được tự bịa luật; các tham chiếu này sẽ được báo lại cho chủ sở hữu spec. Riêng BR-01.4 ("Gợi ý" sắp xếp mặc định) là mơ hồ thật sự và tạm dùng `order_index`.
4. `topic_versions.item_version_ids` không mang thứ tự và trạng thái hiển thị, nên **không tái hiện được chính xác tập item của một lượt học đã chốt**. F01 đọc được vì lấy thứ tự từ version row, nhưng đây là nợ dành cho F04/F10, không phải thứ F01 giải quyết.
5. Chọn schema v1 nghĩa là lưu vocabularies inline cộng `linked_global_id`, tức **bỏ hướng "tham chiếu từ điển toàn cục + snapshot"** mà `topic_learning_data_model.md` đề xuất. F01 chỉ đếm nên không bị ảnh hưởng; F02/F03 sẽ phải sống với quyết định này.
6. `material/` đang untracked, và `topic_learning_data_model.md` trỏ tới `docs/usecase_spec/topic-learning/00-module.md` không tồn tại → **chưa rõ spec-of-record nằm ở đâu**. Ảnh hưởng tới việc sau này ai là người cập nhật spec.
7. Khoá sắp xếp có thể trùng nhau: `order_index` chỉ unique theo level (theo `topic_learning_data_model.md` §3.2) và thời lượng chắc chắn trùng. Nếu con trỏ phân trang không phải cặp (khoá sắp xếp, định danh), người học sẽ thấy topic **trùng hoặc mất** khi cuộn — loại lỗi không lộ ra khi test bằng mock.
8. `topics.duration_minutes` phải là giá trị **được hệ thống bảo trì** để làm khoá sắp xếp có index, nhưng F01a là read-only nên **không có đường ghi nào để bảo trì nó**; trong F01a giá trị này do seed ghi. Đây là điểm chưa bền, phải chốt khi làm F05–F10.
