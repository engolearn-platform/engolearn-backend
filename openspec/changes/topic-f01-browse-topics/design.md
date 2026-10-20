## Context

Xem `proposal.md` để biết lý do. Phần này chỉ ghi trạng thái và ràng buộc đang định hình cách làm.

Trạng thái hiện tại của repo:

- Đã có entity cho topic và user, DTO, hai contract (`topic.contract.TopicSummary`, `user.contract.UserProgress`) và một interface service `topic.service.TopicBrowseService` — nhưng **không có impl, không có repository, không có controller, không có auth**.
- Entity đang lệch `material/topic-learning-schema-v1.json`: `TopicItem` mang cả định danh lẫn toàn bộ nội dung, cộng `topic_version_id`/`version`/`change_type`; `topic_versions` được khai báo là `@Document("topics_version")`; `Topic.latestPublishedVersion` là `Integer`; `ContentStatus` có `BETA` và `ARCHIVED`; `TopicItemSummaryResponse` mang `ContentStatus` và import `user.contract.dto`, tức quan hệ topic → user.
- Môi trường: không có MongoDB, không có testcontainers, không có embedded Mongo; Docker Desktop đang tắt. `docker-compose.yaml` ở root có mongodb và mongo-express; `init-mongo.sh` chỉ tạo user và collection `init_marker`, **không seed dữ liệu**.
- Baseline build xanh (`mvnw -DskipTests compile`).

Ràng buộc kiến trúc: `docs/architecture/package-structure.md` (feature-first, layer-second), `docs/architecture/cross-feature-communication.md` (chỉ giao tiếp qua `contract`), và `ArchitectureTest`.

## Goals / Non-Goals

**Goals:**

- Một đường đọc chạy được cho UC01/UC02, đi đúng controller → service → repository.
- Entity khớp schema v1 ở đúng những chỗ F01 chạm tới.
- Danh sách topic cuộn được, không trùng và không sót topic giữa hai trang.
- Có dữ liệu dev để chạy và smoke test HTTP.

**Non-Goals:**

- Không thiết kế lại release manifest thành cấu trúc mang đủ thứ tự và trạng thái hiển thị.
- Không chạm feature `user` hay `dictionary`.
- Không thêm Maven dependency, Maven module, hay top-level package.
- Không viết lại shape của vocabularies/expressions/quizzes (việc của F02/F03, material chưa có).

## Decisions

### D1. Bám schema v1: tách định danh item khỏi nội dung

`topic_items` chỉ giữ định danh. Tạo `topic_item_versions` chứa context, vocabularies, expressions, quizzes, order, status, version, duration. `topic_versions` thêm manifest `item_version_ids`.

*Alternatives:* (a) giữ shape hiện tại — loại, vì `TopicItem` trộn định danh với snapshot nên mọi feature sau sẽ nhân lên độ lệch; (b) reset theo `topic_learning_data_model.md` — loại, vì cần chốt 8 điểm §10 và material F05–F10 chưa có.

### D2. Duration, và vì sao nó phải nằm trên document topic

Thêm `duration_minutes` vào `topic_item_versions`. `topics.duration_minutes` giữ lại như **giá trị hệ thống bảo trì** (giá trị tổng hợp của các item đang hiển thị), không phải input của Content Manager.

Phân trang dạng cuộn (D9) buộc sắp xếp theo thời lượng phải dùng một khoá **đã lưu và đánh index trên document topic**: không thể cộng tổng thời lượng của document con trong lúc phân trang mà vẫn giữ được index và con trỏ. Vì vậy giá trị tổng hợp phải tồn tại trên topic. Hiển thị vẫn bằng tổng thời lượng của các item đang hiển thị; chỉ nguồn là giá trị đã bảo trì, không phải phép cộng tại chỗ.

*Alternatives:* dùng `context.durationSec` (giấu duration trong bối cảnh, lệch đơn vị so với UC02, và vẫn không dùng được làm khoá sort); đặt trên `topic_items` (duration đổi mà không tạo version, lệch với release đã chốt).

### D3. Trạng thái

`Topic` không có field status: trạng thái "đã phát hành" suy ra từ con trỏ tới bản phát hành hiện hành, và con trỏ đó đổi từ `Integer` sang tham chiếu version. Thêm `HIDDEN` vào tập trạng thái nội dung để biểu diễn item bị ẩn. `BETA` và `ARCHIVED` giữ nguyên vì là drift ngoài scope F01.

*Alternatives:* tạo enum riêng cho từng collection (sát schema v1 hơn nhưng thêm hai enum chưa ai dùng); thêm field status cho topic (schema v1 không có, và tạo hai nguồn trạng thái phải đồng bộ).

### D4. Resolve tập item qua manifest của bản phát hành

Chi tiết topic đọc theo đường: topic → bản phát hành hiện hành → `item_version_ids` → `topic_item_versions`; lọc `status != HIDDEN`; sắp theo `order` của version.

*Alternatives:* query thẳng `topic_item_versions` theo topic và status đã phát hành, bỏ qua manifest — đơn giản hơn nhưng bỏ mất khái niệm "tập item của một bản phát hành", đúng thứ BR-M.5 cần về sau.

### D5. Ranh giới layer và DTO

Controller chỉ gọi service, chỉ trả response DTO; repository chỉ service dùng; entity không ra khỏi service. DTO response được dựng lại theo bảng hiển thị của UC01/UC02: bỏ mô tả khỏi thẻ danh sách, thêm số bài tập, và bỏ `ContentStatus` khỏi tóm tắt item vì UC02 cần trạng thái học còn F01a không có dữ liệu đó.

### D6. F01a không chạm feature `user`

Không trả tiến độ, không badge, không lọc theo trạng thái học. Bộ lọc level rút còn "tất cả" và "một level chỉ định" vì không có nguồn level người học. Điều này cũng gỡ luôn quan hệ topic → user mà DTO hiện tại đang mang.

*Alternatives:* F01b đầy đủ — kéo theo nguồn level mới, xử lý auth, và một thay đổi bảng phụ thuộc đã duyệt.

### D7. Seed data cho môi trường dev

Một runner đặt trong package `config`, chỉ hoạt động ở profile dev, đọc dữ liệu mẫu và chỉ ghi khi collection đang trống.

*Alternatives:* mở rộng `init-mongo.sh` (phải dựng lại volume mới chạy lại, khó sửa); import tay qua mongo-express (không lặp lại được, không idempotent).

### D8. Kiểm thử

Unit test cho service với repository mock, test tầng web bằng MockMvc với service mock, cộng `ArchitectureTest` và `mvnw verify`. Tính đúng của query MongoDB **không được kiểm tự động** trong môi trường này.

*Alternatives:* thêm embedded Mongo (dependency mới, cần approve); testcontainers (Docker đang tắt).

### D9. Phân trang dạng cuộn

Danh sách topic trả theo trang cuộn, không theo số trang. Mặc định 20 topic mỗi trang; `limit` tuỳ chọn với trần 100; `limit` nhỏ hơn 1 hoặc lớn hơn 100 bị từ chối là lỗi yêu cầu không hợp lệ. Phản hồi gồm danh sách topic, con trỏ cho trang kế tiếp và dấu còn trang hay không; trang cuối không kèm con trỏ. Không trả tổng số topic.

Con trỏ là **cặp `(khoá sắp xếp, định danh)`**: `(orderIndex, id)` cho thứ tự gợi ý, `(durationMinutes, id)` cho thứ tự thời lượng. Query phải sắp theo cả hai trường và so sánh trên cả cặp, nếu không sẽ trùng hoặc sót topic giữa hai trang. Con trỏ chỉ có nghĩa với đúng bộ lọc và cách sắp xếp đã sinh ra nó; con trỏ không giải mã được bị từ chối.

*Alternatives:* phân trang theo offset — đơn giản hơn nhưng trùng hoặc mất topic khi dữ liệu đổi giữa hai lần gọi, đúng tình huống danh sách publish có thể gặp; trả hết rồi cắt ở client — không chịu được khi số topic tăng.

### D10. Quy ước API

Danh sách ở `/api/v1/topics`, chi tiết ở `/api/v1/topics/{topicId}`. Field JSON dùng camelCase.

Tên field Java đã camelCase nên payload mặc định đã camelCase; `@Field(...)` snake_case chỉ ảnh hưởng tầng Mongo. Vì vậy đây là quy ước cần ghi lại, không phải cấu hình cần thêm.

*Alternatives:* `/api/topic` số ít không phiên bản — loại vì không có chỗ đặt phiên bản khi contract đổi.

## Risks / Trade-offs

- [Query MongoDB không được verify tự động] → Reviewer đọc kỹ criteria và derived query; khai báo thẳng trong báo cáo cuối; smoke test thủ công bằng `docker-compose up -d` khi Docker bật.
- [Khoá sắp xếp trùng nhau] → Con trỏ là cặp `(khoá, định danh)`; unit test phải phủ "cuộn hai trang không trùng không sót"; nhưng tính đúng thật của query chỉ kiểm được khi có MongoDB.
- [Aggregate duration chưa có đường bảo trì] → F01a read-only nên giá trị do seed ghi; đây là nợ phải trả khi làm F05–F10, lúc đó publish phải cập nhật aggregate.
- [Schema v1 không mang đủ manifest] → Chấp nhận nợ có ý thức; F01 đọc được vì lấy thứ tự từ version row; F04/F10 phải bổ sung trước khi làm lượt học dở.
- [Spec thiếu BR-01.4, BR-01.5, BR-02.5, BR-02.6] → Dùng bảng "Chi tiết tham chiếu" trong UC01/UC02 làm nguồn tạm; báo chủ sở hữu spec; thứ tự gợi ý mặc định tạm lấy `order_index`.
- [BREAKING: document theo shape cũ không còn đọc được] → Chưa có dữ liệu thật nên chỉ cần seed lại; không viết script migration.
- [Seed chạy nhầm ở môi trường thật] → Chỉ bind theo profile dev, chỉ ghi khi collection trống.
- [Đổi shape entity có thể làm build hoặc `ArchitectureTest` đỏ] → Chạy `mvnw verify` sau mỗi bước entity.
- [Chọn schema v1 bỏ hướng snapshot từ điển] → F01 chỉ đếm nên không bị ảnh hưởng; F02/F03 phải chấp nhận hoặc đổi sớm.

## Migration Plan

1. Cập nhật entity theo schema v1: thêm `TopicItemVersion`, làm sạch `TopicItem`, thêm manifest cho `TopicVersion`, thêm `duration_minutes`, thêm `HIDDEN`, đổi kiểu con trỏ release.
2. Thêm repository, gồm truy vấn sắp xếp và so sánh trên cặp khoá cho phân trang cuộn.
3. Thêm service, request DTO và response DTO (kèm con trỏ trang kế tiếp).
4. Thêm controller ở `/api/v1/topics` và `/api/v1/topics/{topicId}`.
5. Thêm seed dev.
6. Verify: unit test, test tầng web, `ArchitectureTest`, `mvnw verify`; smoke test HTTP thủ công.

Rollback: revert commit. Không có dữ liệu thật để migrate; nếu DB dev còn document theo shape cũ thì xoá collection rồi chạy lại seed.
