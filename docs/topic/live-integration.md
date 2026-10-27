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
