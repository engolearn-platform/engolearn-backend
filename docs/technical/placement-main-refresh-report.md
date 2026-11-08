# Placement main refresh — kết quả 2026-11-08

## Mã nguồn và phạm vi

- FE: merge commit `bf5a13d`, nhận main `59078ccc15799ca93288078d0642a62f03cf9030`; ahead3/behind0 sau fetch cuối. Merge thử trong worktree `integration/placement-main-refresh`, sau kiểm chứng fast-forward checkout trên D: của nhánh `merge/placement-guest-user-main`.
- BE: code HEAD `852c556` đã chứa main `0f416900f9e6b785682a5073c5bac84f6eee5b08`, ahead5/behind0 trước commit tài liệu. Không cần merge thừa.
- Conflict thực: FE `src/router.tsx`, `src/features/landing-page/routes.ts`. Giữ public shell/preview, TopicDetail/MyTopics, Speaking và proxy /ws từ main; giữ Placement thật, GrammarAdmin và auth return-to từ nhánh feature. Chỉ PlacementRoutes sở hữu /placement.
- Sửa tay thêm đúng phần mô tả Placement trên GuestLandingView: làm Guest, đăng nhập sau nộp để xem kết quả. Không sửa nghiệp vụ module khác. Review tĩnh độc lập không có phát hiện quan trọng.
- Không ghi đè/stage application.yaml đã sửa sẵn; không commit .env hoặc credential. Không push main.

## Bằng chứng mới

| Kiểm tra | Kết quả |
|---|---|
| FE npm ci / build | Pass; còn cảnh báo bundle lớn |
| FE lint | Exit0; 0 errors, 72 warnings |
| Auth return-to | 4/4 pass |
| Placement HTTP qua FE mới → BE8080/Mongo hiện tại | 53/53 pass |
| Placement HTTP qua FE mới → BE8083/DB kiểm thử mới | 53/53 pass |
| Package từ source sạch | Pass; 96/96 WAV hash và 8 negative input regressions |
| Import sạch / Apply lặp | 321 documents mới / lần lặp 0 mới, 0 replace, 0 mismatch; attemptsTouched=false |
| Browser | Landing→Placement thật, start Guest, Listening tiến 00:08/00:15; topics list/detail, grammar preview, learning-path preview hiển thị |
| BE mvnw verify | Không pass tổng: 686 tests, 7 failures, 48 errors, 29 skips |
| So với baseline main | 55 test identity lỗi trùng hoàn toàn; không có identity lỗi mới |
| Riêng Placement | 137 tests: 136 pass, 1 skip quyền symlink Windows; không failure/error |

Nguồn sạch BE được git archive từ852c556, không copy .env/target/database, đặt trong C: không có parent bank. Setup tạo .env/JWT mới và dùng Mongo replica set riêng `engolearn-refresh-check`, DB `engolearn_refresh_check`, host27028. FE dùng npm ci trong worktree riêng, không copy node_modules. Docker lúc đầu tắt khiến BE không kết nối Mongo; khởi động Docker/Mongo đã giải quyết mà không đổi code.

HTTP kiểm tra Guest/start/save/submit, không lộ kết quả trước auth, chặn phiên khác, sai mật khẩu, register/login/claim/result/review, idempotency, logout denial, sáu audio HEAD/GET/Range/checksum. Các kết quả nằm ở workspace C: trong refresh-live-results.json, refresh-clean-results.json, refresh-be-comparison.json và refresh-*.log. Screenshot: refresh-listening-proof.png.

## Dữ liệu và giới hạn

- DB đang dùng chỉ preflight seed; không reimport/replace seed hay sửa attempts cũ. Harness tạo một tài khoản/bài test mới trong DB dev. DB kiểm thử riêng có seed, tài khoản/bài harness và bài browser mới; đã dừng container, giữ dữ liệu để đối chiếu.
- V1/snapshot/attempt lịch sử không đổi; V1 vẫn có URL localhost8892 và còn phụ thuộc audio server cũ khi cần phát lịch sử. V2 dùng API media từ BE, không cần Node8892.
- Đây là kiểm chứng Placement và public route, không phải bảo đảm mọi nghiệp vụ nhóm. Chưa kiểm thử end-to-end Speaking/WebSocket/service bên ngoài, toàn bộ role management, Subscription/payment hoặc mọi luồng Learning Path.
- Chưa chạy browser đầy đủ login/register/reload/claim sau merge; các phần này có HTTP53 và return-to4 hỗ trợ, không thay cho UI end-to-end. Active-attempt/expiry và account-has-result không được chạy lại bằng real HTTP riêng trong đợt này.
- Full BE suite vẫn đỏ ở các test baseline; cần chủ module xử lý riêng. Không sửa nghiệp vụ thành viên khác để làm xanh.

## Chạy và chia sẻ

Dùng nhánh `merge/placement-guest-user-main` ở cả hai repo; đây là nhánh review, chưa vào main. Không chia sẻ .env thật. Hướng dẫn đầy đủ: [placement-local-runtime.md](placement-local-runtime.md).

BE: chạy `tools/placement/setup-local.ps1` để preflight, rồi `-Apply` khi thiếu data, sau đó `tools/placement/run-local.ps1`. FE: `npm ci`, `npm run dev`; mở http://127.0.0.1:3000 với BE8080. Môi trường kiểm thử3001/8083 đã dừng; BE8080 và FE chính3000 được giữ chạy.

Rollback code bằng revert merge có review trên nhánh tích hợp; điểm trước FE làf1737cc. Không reset dữ liệu/volume để rollback. Sửa đổi YAML riêng vẫn unstaged để người dùng quản lý.
