# Bàn giao tích hợp Topic Learning F01–F04

Cập nhật ngày 27/11/2026. Tài liệu dành cho nhóm phụ trách khám phá Topic, học Item, quiz, hoàn thành và học lại. Căn cứ nghiệp vụ là bộ use case Topic Learning F01–F04, cùng BR-M.5/7/9 và BR-F10.8/9 về version, visibility và publication.

P05 ngày 26/11/2026 đã nghiệm thu Manager và thao tác publication trên checkout tích hợp thử nghiệm. Tại mốc đối chiếu Backend `dev` `4005213cde3494affaa4f3f8f779325c95f99e5b`, endpoint Manager Unpublish và marker suspension P04 chưa có. Các mô tả về P03–P05 dưới đây ghi hợp đồng và bằng chứng từ checkout thử nghiệm. Khi triển khai phía Learning, kiểm tra phiên bản Backend thực tế đã có hợp đồng publication tương ứng.

## F01 — Khám phá Topic và quyền truy cập

P04 ghi trạng thái bằng `Topic.publicationSuspended` trong trường Mongo `topics.publication_suspended`. Với Topic đã có release, giá trị vắng mặt hoặc `false` giữ hành vi legacy. Khi marker là `true`, Manager đọc trạng thái `unpublished`. Topic vẫn giữ `latestPublishedVersion`, TopicVersion trạng thái `PUBLISHED`, manifest, ItemVersion và media references. Visibility từng Item tiếp tục thuộc F06.

| Điểm tích hợp | Trách nhiệm phía Learning |
| --- | --- |
| `GET /api/v1/topics` và `GET /api/v1/topics/{topicId}` | `TopicBrowseController`, `TopicLearnerCatalogServiceImpl.discover/detail` và `TopicBrowseServiceImpl.getAvailableTopics/getTopicDetail`. Áp dụng suspension cho discovery và public detail. Tính lại tổng và phân trang sau khi áp dụng điều kiện. Kiểm tra cả khách và người đã đăng nhập. |
| `GET /api/v1/topics/mine` | `TopicLearnerCatalogServiceImpl.mine`. Áp dụng marker cho cả hai bộ lọc `IN_PROGRESS` và `COMPLETED` cùng bộ đếm. Giữ nguyên dữ liệu progress. |
| `POST /api/v1/topic-items/{itemId}/attempts` | `TopicAttemptServiceImpl.start` và `TopicRetryServiceImpl.startItem`. Chặn việc cấp lượt LEARN hoặc RETRY mới khi suspended. Kiểm tra riêng replay hợp lệ và đường tiếp tục một lượt đã có quyền truy cập. |

Kết quả live P04/P05 trên fixture suspended: guest detail trả `200`, catalog khách và My Topics vẫn có Topic, fresh LEARN trả `200`. Một attempt có sẵn vẫn đọc được đúng version đã ghim. Đây là khoảng trống tích hợp BR-01.1, BR-F10.9 và V07/L06. Tiến trình và attempt đã lưu được giữ trong kiểm tra P04/P05.

## F02 — Học Item và giữ phiên bản đã ghim

- Lượt LEARN mới chọn ItemVersion đã phát hành trong manifest hiện hành, dùng quyền, persisted CEFR và điều kiện bắt đầu. Mỗi user/topic/generation có một LEARN attempt đang chạy. Draft và MAJOR working copy vẫn ở luồng Manager cho đến batch commit.
- Khi tiếp tục, truy xuất attempt theo ownership, generation và ItemVersion đã ghim. Giữ frontier, revision và lịch sử. P05 kiểm chứng bản đã ghim không đổi sau MINOR, sau MAJOR và khi Manager gỡ xuất bản. Đường đọc cần giữ ranh giới này khi thêm điều kiện discoverability.
- `GET /api/v1/topic-attempts/{attemptId}` đọc trạng thái. `GET .../content` đọc nội dung ghim cho lượt đang học. `POST .../advance` tăng frontier từ đơn vị nội dung hiện tại bằng revision, stage và itemIndex. `POST .../access` chỉ ghi nhận recency. Luồng nội dung theo thứ tự CONTEXT, VOCAB, EXPRESSIONS, FILL_BLANK, SITUATIONAL_CHOICE và bỏ qua phần không có dữ liệu.
- F06 HIDDEN là quy tắc riêng. Kiểm tra denial khi bài bị ẩn và khôi phục truy cập bằng đúng version khi Show. Mã hiện có còn đường reconcile khi người học gọi start rõ ràng, có thể CAS-abandon một lượt đang chạy trên bài đã ẩn để giải phóng slot. Đối chiếu BR-M.7 và L05 trước khi thay đổi đường này. P05 xác minh version ổn định khi Hide/Show. Chuỗi pause/resume của retry đang hoạt động chưa được chạy.

Chi tiết response, authorization và replay tại [Topic attempts and quiz](quiz-api-handoff.md).

## F03 — Quiz, grading và replay

- `GET /api/v1/topic-attempts/{attemptId}/quiz` đọc câu đang chờ. `GET .../quiz-navigation` trả `attempt` và `entries`. Câu chưa tới hoặc chưa trả đúng giữ giới hạn tiết lộ theo trạng thái LOCKED/CURRENT/COMPLETED.
- `POST .../quizzes/{quizId}/answers` dùng revision, optionId và `Idempotency-Key`. Câu sai ghi submission và tăng revision, giữ frontier. Câu đúng tiến đến câu chưa trả tiếp theo. Đáp án, correct feedback và first-try correctness chỉ được trả ở trạng thái cho phép. Điểm và correctness do Backend tính.
- quizId và optionId gắn với ItemVersion và vị trí trong dữ liệu ghim. MAJOR có thể đổi cấu trúc ở version mới. MINOR chỉ sửa trường cho phép trên version hiện có. Giữ phiên bản và cấu trúc grading khi đọc/chấm lượt cũ.
- Accepted replay cùng fingerprint có thể trả response cũ. Changed fingerprint trả `409 IDEMPOTENCY_KEY_REUSED`. Khi cần frontier mới, đọc lại attempt. Giữ semantics riêng của LEARN và RETRY về thứ tự replay, ownership, generation và visibility.

P05 có kiểm tra Backend rằng response trước grading không lộ đáp án. Chấm và hoàn tất một lượt đã ghim trong lúc suspended hoặc sau MAJOR vẫn cần live acceptance. [Topic attempts and quiz](quiz-api-handoff.md).

## F04 — Hoàn thành, kết quả, học lại và reset

- Final correct answer commit completion, result và receipt theo cơ chế CAS của lượt học. `GET .../result` và `GET .../review` phải đọc đúng kết quả/các câu đã học theo quyền và generation. Kết quả chính thức cũ vẫn có hiệu lực trong khi ITEM retry chưa hoàn tất. Khi retry hoàn tất, kết quả hợp lệ mới thay thế kết quả cũ.
- ITEM retry bắt đầu trên phiên bản mới nhất đã phát hành của bài đủ điều kiện. Tiếp tục một runtime đang hoạt động phải dùng snapshot của lượt đó. `GET /api/v1/topic-retry-sessions/{retrySessionId}` chỉ đọc trạng thái, manifest đã chốt và Item đang chạy hoặc kế tiếp.
- **H01 / A07 — TOPIC retry mới đang retired.** `TopicRetryController` gọi `TopicRetryServiceImpl.startTopic`. Request mới tới `POST /api/v1/topics/{topicId}/retry-sessions` trả `410 TOPIC_RETRY_RETIRED`. Exact replay legacy hợp lệ và tiếp tục session legacy vẫn có đường riêng. `startItem` tạo lượt ITEM. FE hiện ưu tiên item retry, còn adapter topic retry cũ vẫn tồn tại. Nếu khôi phục TOPIC retry theo BR-09.1/4/7/9/10, phải chốt contract với Product Owner và FE trước khi bật entry point. Một lượt TOPIC cần manifest có thứ tự gồm Item ID và ItemVersion ID, giữ nguyên snapshot qua MAJOR, áp dụng quy tắc skip/pause đã chốt và chỉ có một lượt active trong scope được quy định.
- Reset là luồng hiện hành riêng. `POST /api/v1/topics/{topicId}/reset` tăng generation bằng revision/generation check và receipt, tách tiến độ hiệu lực mới khỏi dữ liệu cũ. Các handle đã lỗi thời có thể trả `TOPIC_RESET`, `RESULT_CHANGED` hoặc `RETRY_SESSION_CHANGED` tùy luồng. Giữ nguyên dữ liệu lịch sử và semantics hiện hành khi thêm suspension.
- Kiểm chứng riêng dấu hiệu có nội dung mới đối với Topic đã hoàn thành, kết quả chính thức sau retry, progress khi append và hành vi legacy. P05 giữ progress và các pin trong Mongo qua các lệnh Manager. Các kịch bản F04 nói trên vẫn cần nghiệm thu phía Learning.

Chi tiết tại [Topic completion and results](completion-api-handoff.md) và [Topic review, retry and reset](retry-api-handoff.md).

## Nghiệm thu tích hợp

1. Trên một Topic v1, tạo attempt LEARN và ITEM RETRY phù hợp điều kiện. Ghi ItemVersion, saved position, revision, manifest và kết quả hiện hành. Nếu có TOPIC retry hợp lệ theo contract đã chốt, ghi thêm frozen snapshot của lượt đó.
2. Gỡ xuất bản. Kiểm tra discovery, public detail, My Topics và bộ đếm. Thử bắt đầu LEARN/RETRY mới bằng actor đủ điều kiện. Sau đó thử tiếp tục và hoàn tất các lượt được phép còn dở, đối chiếu pin, vị trí, đáp án và kết quả. Kiểm tra riêng ITEM HIDDEN/Show và replay hợp lệ.
3. Republish khi dữ liệu không đổi và khi có MAJOR hoặc bài mới. Trường hợp đầu giữ release ID và ItemVersion. Trường hợp sau tạo một successor, giữ release cũ và các version không đổi. Kiểm tra lượt đã ghim, lượt mới và các trạng thái hoàn thành sau append.
4. Kiểm tra quyền đọc historical media, delivery/playback thực từ provider test, và answer secrecy. P05 đã kiểm tra metadata, tham chiếu giữ lại và một số response contract. Cloudinary upload/delivery/playback, retry/grading live trong scenario suspension và notification delivery vẫn ở trạng thái NOT_RUN.
5. Chạy FE Learner API contract riêng. Hai lỗi P00 đã tái hiện ở P05 là `20 !== 17` operations và documented success example thiếu `entries`. Theo dõi chúng riêng với H01 và H02/A09.

## Phạm vi và bằng chứng

- **Đã kiểm chứng ở P05:** draft isolation, LEARN pin khi MINOR/MAJOR, đọc attempt đã ghim trong khi suspended, lịch sử/metadata media và progress ổn định, republish cùng release hoặc một successor và visibility Manager. Toàn bộ tích hợp ở trạng thái NOT RELEASE READY.
- **Bằng chứng làm việc:** thư mục `e2e-artifacts/topic-authoring-management` ở workspace EngoLearn có `P00/learner-handoff.md` (A02/A03/A04–A06/A07/A08/A09/A10/A11), `P04/learner-handoff.md`, `P05/logs/learner-boundary-p05.log`, `P05/acceptance-matrix.md` (L01–L08) và `P05/verification-report.md`. File này tập hợp nội dung cần bàn giao từ các mốc đó.
- **Notification downstream:** consumer chỉ dùng các dữ kiện từ release đã commit gồm release ID, predecessor và changed Item/ItemVersion. Release được reuse và thay đổi MINOR không phát sinh MAJOR fact mới. Chọn người nhận, kênh, retry, dedup và delivery vẫn là phần việc downstream. Bằng chứng producer nằm ở `P05/notification-facts.json`.
