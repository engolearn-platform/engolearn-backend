## 1. Entity theo schema v1

- [x] 1.1 Tạo `topic.model.TopicItemVersion` với `@Document("topic_item_versions")` và các field theo `material/topic-learning-schema-v1.json`: `topic_item_id`, `topic_id`, `topic_version_id`, `version`, `change_type`, `previous_item_version_id`, `title`, `description`, `order`, `status`, `duration_minutes`, `context`, `vocabularies`, `expressions`, `quizzes`, `published_at`; verify bằng `mvnw -q -B -DskipTests compile` chạy xanh.
- [x] 1.2 Làm sạch `topic.model.TopicItem` để chỉ còn định danh `topic_id`; verify bằng compile xanh và grep xác nhận không còn field nội dung (`context`, `words`, `expressions`, `quizzes`) trên `TopicItem`.
- [x] 1.3 Thêm `item_version_ids` vào `topic.model.TopicVersion` và đổi tên collection thành `topic_versions`; verify bằng compile xanh và đọc lại annotation `@Document`.
- [x] 1.4 Đổi con trỏ release trên `topic.model.Topic` sang tham chiếu id của bản phát hành (thay cho `Integer latestPublishedVersion`); verify bằng compile xanh và grep xác nhận không còn chỗ nào dùng con trỏ như số nguyên.
- [x] 1.5 Thêm `HIDDEN` vào `topic.model.enums.ContentStatus`; verify bằng compile xanh và rà lại mọi chỗ dùng enum để chắc không vỡ.
- [x] 1.6 Chạy `mvnw verify` sau khi entity đổi xong để chắc build và `ArchitectureTest` vẫn xanh; verify bằng output của lệnh.

## 2. Repository

- [x] 2.1 Thêm `topic.repository.TopicRepository` với truy vấn danh sách topic đã phát hành (con trỏ release khác null), hỗ trợ lọc theo `cefr_level`, tìm một phần theo tên trên `title_en` và `title_vi` không phân biệt hoa thường, sắp xếp theo cặp khoá `(order_index, id)` hoặc `(duration_minutes, id)`, và nhận điều kiện so sánh trên cả cặp cho con trỏ phân trang; verify bằng compile xanh cộng review criteria đối chiếu acceptance của spec (chưa có MongoDB để chạy).
- [x] 2.2 Thêm `topic.repository.TopicVersionRepository` để lấy bản phát hành theo id; verify bằng compile xanh.
- [x] 2.3 Thêm `topic.repository.TopicItemVersionRepository` để lấy các item version theo danh sách id; verify bằng compile xanh.

## 3. Service và DTO

- [x] 3.1 Dựng lại `topic.dto.response.TopicSummaryResponse` theo thẻ topic của UC01 (ảnh, tên, level, số từ vựng, số bài tập, thời lượng) và bỏ mô tả; verify bằng compile xanh và đối chiếu từng field với bảng "Chi tiết tham chiếu" của UC01.
- [x] 3.2 Dựng lại `TopicDetailResponse` và `TopicItemSummaryResponse` theo UC02: giữ tên/level/mô tả ở cấp topic, ở cấp item chỉ còn tên tình huống, mô tả ngắn, thời lượng, số từ vựng, số mẫu câu, số bài tập; bỏ `ContentStatus` và bỏ `currentUserProgress`; verify bằng compile xanh và grep xác nhận không còn import nào của feature `user`.
- [x] 3.3 Thêm request DTO cho danh sách (level tuỳ chọn, từ khoá tuỳ chọn, cách sắp xếp, `limit` tuỳ chọn, con trỏ tuỳ chọn) và chi tiết (định danh topic); verify bằng compile xanh.
- [x] 3.4 Dựng response DTO cho danh sách có con trỏ trang kế tiếp và dấu còn trang hay không, không có tổng số topic; verify bằng compile xanh và đối chiếu requirement "Phân trang dạng cuộn cho danh sách topic".
- [x] 3.5 Cài `TopicBrowseServiceImpl`: resolve tập item qua `item_version_ids` của bản phát hành hiện hành, lọc bỏ item `HIDDEN`, sắp theo `order` của version, tính số liệu tổng hợp bằng cách đếm trên các item đang hiển thị, và dựng con trỏ từ topic cuối cùng của trang; verify bằng các unit test ở 3.6.
- [x] 3.6 Viết unit test cho `TopicBrowseServiceImpl` với repository mock, phủ: chỉ topic đã phát hành; lọc theo một level; không giới hạn level; tìm theo tên khớp một phần; sắp mặc định theo `order_index`; sắp theo thời lượng; item bị ẩn không xuất hiện và không đóng góp số liệu; thời lượng tính từ item đang hiển thị; topic không tồn tại trả rỗng; topic chưa phát hành trả rỗng; level không hợp lệ bị từ chối; mặc định 20 topic mỗi trang; `limit` hợp lệ và `limit` ngoài khoảng bị từ chối; cuộn từ trang 1 sang trang 2 không trùng không sót; trang cuối không có con trỏ; con trỏ không giải mã được bị từ chối; verify bằng `mvnw -B -Dtest=TopicBrowseServiceImplTest test` xanh.

## 4. Controller

- [x] 4.1 Thêm controller trong `topic.controller` với danh sách ở `/api/v1/topics` và chi tiết ở `/api/v1/topics/{topicId}`; verify bằng compile xanh cộng review xác nhận controller chỉ gọi service và chỉ trả response DTO, không chạm repository hay entity.
- [x] 4.2 Ánh xạ lỗi: topic không tồn tại hoặc chưa phát hành trả về không tìm thấy; level không hợp lệ, `limit` ngoài khoảng, và con trỏ không hợp lệ trả về lỗi yêu cầu không hợp lệ; verify bằng các test tầng web ở 4.3.
- [x] 4.3 Viết test tầng web (MockMvc với service mock) cho hai endpoint: danh sách trả đúng số liệu và không chứa mô tả, và trả con trỏ khi còn trang; chi tiết trả item theo đúng thứ tự; không tìm thấy trả đúng mã lỗi; level không hợp lệ trả đúng mã lỗi; `limit` ngoài khoảng trả đúng mã lỗi; verify bằng `mvnw -B test` xanh.
- [x] 4.4 Xác nhận payload JSON dùng camelCase và ghi lại quy ước (tên field Java đã camelCase nên không cần cấu hình thêm); verify bằng cách đọc tên field thật trong test tầng web ở 4.3.

## 5. Seed data cho dev

- [x] 5.1 Thêm dữ liệu mẫu và runner trong package `config`, chỉ hoạt động ở profile dev: ít nhất 2 topic ở 2 level khác nhau, mỗi topic 5–20 item đang hiển thị, trong đó có ít nhất một item bị ẩn, và `duration_minutes` trên mỗi topic được ghi bằng tổng thời lượng item đang hiển thị; verify bằng `mvnw verify` xanh và xác nhận runner không hoạt động khi profile dev tắt.
- [x] 5.2 Bảo đảm runner chỉ ghi khi collection trống, chạy lại không nhân bản dữ liệu; verify bằng chạy hai lần liên tiếp và đối chiếu số document (cần MongoDB đang chạy; nếu không có thì ghi rõ là chưa verify).

## 6. Verification tổng thể

- [x] 6.1 Chạy `mvnw verify` trên toàn repo và ghi lại kết quả thật, kể cả phần không chạy được; verify bằng output của lệnh được dán vào báo cáo.
- [x] 6.2 Smoke test HTTP thủ công: bật Docker Desktop, chạy `docker-compose up -d`, chạy app với profile dev, gọi `/api/v1/topics` và `/api/v1/topics/{topicId}` và đối chiếu spec (thẻ topic có ảnh, tên, level, số từ vựng, số bài tập, thời lượng và không có mô tả; chi tiết trả item theo thứ tự, không lộ nội dung học và không lộ tiến độ); gọi lại danh sách với `limit` nhỏ rồi dùng con trỏ để lấy trang 2, xác nhận không trùng không sót; verify bằng response thật dán vào báo cáo.
- [x] 6.3 Xác nhận `ArchitectureTest` xanh và không có phụ thuộc cross-feature mới; verify bằng `mvnw test` cộng grep xác nhận `TopicItemSummaryResponse` không còn import `user.contract.dto` và không có file topic nào import feature khác ngoài `dictionary.contract`.
- [x] 6.4 Báo lại cho chủ sở hữu spec rằng `rules.md` của f01 đang thiếu BR-01.4, BR-01.5, BR-02.5, BR-02.6 trong khi UC01/UC02 tham chiếu chúng, và thứ tự "Gợi ý" mặc định của BR-01.4 chưa được định nghĩa; verify bằng việc xác nhận đã thông báo và ghi lại trong báo cáo cuối.
