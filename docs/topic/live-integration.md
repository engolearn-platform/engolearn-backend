# Live integration — public catalog slice

No fixtures, DB resets or seeded data. Existing MongoDB data and existing browse/detail services remain the source.
New GET /api/v1/topic-learning/viewer reports GUEST because the application has no authentication module. It does not use MockCurrentUserProvider or MockLearnerLevelProvider.
Browse responses add total and visible totalItemCount. Detail adds actual totals/cover metadata. Personalized progress is null; start/retry actions require sign-in. Existing learning endpoints using the legacy fixed learner are outside this slice and are not exercised by the FE.
Existing collection/schema are unchanged. Counts resolve published releases and visible items from DB.
Verification: package without mock tests, FE build/lint, real HTTP through FE proxy, browser catalog -> detail. Actual results recorded after running.

## Bằng chứng lát public (2026-10-27)

BE package PASS với -Dmaven.test.skip=true; không chạy test mock. FE build PASS (cảnh báo chunk cũ), lint0 errors/70 warnings.
FE http://localhost:3000 -> BE http://localhost:8080. Viewer200 GUEST, không dùng provider user/level giả.
API trực tiếp và proxy trả cùng3 topic/cùng ID; pagination2+1 không trùng, total3, empty search0, detail404 đúng.
Browser thật: lọcB1 -> detail Phong van xin viec -> back giữfilter; reset/search san bay -> detail O san bay. Metadata detail18 từ vựng bằng tổng6 item; không tiến độ giả.
Preview4317/4318 đã dừng, launcher và serverfixture đã xóa. DB không bị seed/reset/migrate, không gọi learning mutation.
Chỉ public read slice được xác minh; My Topics/start/attempt/quiz/retry chưa được xác minh. User đã chọn dùng hồ sơ học viên thật trong DB qua cấu hình local cho lát kế tiếp, chưa có auth.

## Lát học với hồ sơ local — triển khai

User duyệt hồ sơ thật trong DB, chọn bằng TOPIC_LOCAL_LEARNER_ID; chưa có auth. Tạo hồ sơ qua local-profiles (loopback only), không tự seed topic/tiến độ. Provider cố định đã gỡ khỏi Spring runtime.
Catalog cá nhân loại topic đã bắt đầu trước khi phân trang; mine/outline/quyền theo profile/DB. Start lưu một item attempt thật tại CONTEXT/0; unique partial index chặn hai item LEARN dở trong cùng topic. Không tạo thêm parent session cho LEARN item độc lập; parent cũ vẫn được đọc.
Advance dùng CAS trên @Version của chính document attempt, lưu receipt cùng vị trí trong một ghi MongoDB; không cần giả lập transaction đa document trên standalone. Access dùng CAS, giữ revision. Arbitrary PUT position trả410, không còn đường nhảy bước qua route cũ.
Context/Vocab/Expressions trả snapshot đã pin. IDs purpose/example được suy ổn định từ version+vị trí trong snapshot bất biến, không bịa nội dung/media. Quiz/result/retry chưa triển khai ở lát này.

## Lát hồ sơ local và học nội dung — đã chạy thật (2026-10-27)

- User chọn hồ sơ lưu DB qua cấu hình local, cho phép chọn tên/level tiện kiểm tra. Đã tạo Học viên local/A2 trong topic_learner_profiles và chọn ID qua TOPIC_LOCAL_LEARNER_ID ở .env gitignored. Không seed/reset topic/version/quiz/tiến độ.
- MockCurrentUserProvider/MockLearnerLevelProvider đã bỏ khỏi Spring runtime. Viewer và mọi API học đọc cùng hồ sơ thật. Cấu hình này chưa phải auth; khi chọn local learner, API topic chỉ nhận loopback.
- FE3000 proxy BE8080; preview4317/4318 đã gỡ. CORS cho phép đúng localhost:3000 và127.0.0.1:3000. Lỗi browser đầu tiên là403 CORS, đã xác định bằng request có Origin và sửa; không phải500 như nhận định ban đầu.
- Browser thật: mở topic -> bắt đầu CONTEXT/0 -> VOCAB -> reload -> xem lại Context giữ14% -> quay frontier -> học3 từ và2 purposes -> FILL_BLANK/0 tại85%, revision6.
- Topic đã rời discover (3->2), mine có1 IN_PROGRESS, completed0. Sidebar thật ghi3/18 từ,4/24 mẫu câu,0/6 quiz;0/6 bài hoàn thành đúng vì chưa làm quiz.
- Request replay cùng key/body chỉ tăng revision1 lần; revision cũ trả409 không đổi vị trí. Hồ sơA2 start topicB1 trả422 LEVEL_TOO_LOW.
- PUT item position cũ và GET item content cũ trả410; chỉ content theo owned attempt được dùng. Các route learner legacy redirect về detail thật, không mount provider/mock cũ.
- Nội dung như word1/word2 và các URL media lấy từ snapshot DB hiện có; không thêm nội dung minh họa hoặc fallback ảnh/audio giả. Media playback không được xác minh.
- BE package PASS với maven.test.skip=true; FE build PASS và lint0 errors/70 warnings. Không chạy mocked suites, không chạy mvn verify đầy đủ. Bằng chứng là API và browser thật trong phạm vi trên.
- Quiz/answer/result/review/retry đầy đủ vẫn là lát sau; không đánh dấu item/topic COMPLETED giả. Chưa xác minh nhiều profile đồng thời, publish/hide/version changes hoặc phục hồi lỗi hạ tầng.
- Personal catalog hiện đọc tập published để tính/filter/cursor trước phân trang; chưa tối ưu truy vấn cho catalog lớn.

Cách chọn hồ sơ: POST /api/v1/topic-learning/local-profiles với displayName và cefrLevel thật, lưu learnerId trả về vào TOPIC_LOCAL_LEARNER_ID trong .env rồi restart BE. Endpoint tạo hồ sơ chỉ nhận loopback. Không commit .env.
