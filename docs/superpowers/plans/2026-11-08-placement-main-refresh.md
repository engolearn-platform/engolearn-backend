# Placement Main Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans task by task. Thực hiện tuần tự; ghi bằng chứng và kết quả trước khi đánh dấu hoàn tất.

**Goal:** Cập nhật main vào nhánh tích hợp, giữ Placement chạy trên máy tác giả và bản checkout sạch, bảo toàn hành vi các module khác.
**Architecture:** Merge trong checkout/worktree riêng, nhận nguyên thay đổi của main và chỉ sửa các điểm tích hợp Placement. BE hiện không thiếu commit main nên không merge thừa; FE ghép route mới của main với route Placement hiện có. Xác minh bằng FE proxy, BE và MongoDB replica set thật.
**Tech Stack:** React19/Vite6/TypeScript/React Router7; Java21/Spring Boot; MongoDB; Docker; PowerShell.
**Spec:** Yêu cầu đã thống nhất trong chat và mục Phạm vi bên dưới; tham chiếu thiết kế portable tại ../specs/2026-11-08-placement-portable-design.md.

## Phạm vi và trạng thái đã quan sát

- Workspace: D:/TrenLop/WWWJava/Nhom/EngoLearnApp; mỗi thư mục engolearn-backend và engolearn-frontend là một repo độc lập.
- BE HEAD852c556; origin/main0f41690: ahead5, behind0; application.yaml có sửa đổi chưa commit của người dùng.
- FE HEADf1737cc; origin/main59078cc: ahead2, behind29; working tree sạch.
- Đã fetch cả hai repo. Merge-tree ba cây, không write-tree, dự báo marker conflict ở src/router.tsx và src/features/landing-page/routes.ts. Đây là dự báo; merge thực có thể thay đổi nếu main tiến thêm.
- Main FE có PublicResourceLayout, TopicDetailRoutes, MyTopicsRoutes, LearningPathPreviewView, Speaking routes/constants và proxy /ws.
- Main landing routes vẫn đăng ký placeholder /placement, trùng với Placement thật trên nhánh tích hợp.
- AuthForm khác giữa hai tip nhưng KHÔNG nằm trong danh sách thay đổi mới từ merge-base tới main. Không suy diễn rằng main vừa đổi auth; merge phải bảo toàn sửa return-to của nhánh tích hợp.

## Global Constraints

- Không sửa nghiệp vụ Speaking, Topic, Assessment, Learning Path, Grammar hoặc Subscription để che lỗi tích hợp.
- Nhận nguyên các thay đổi thuộc những module đó từ main; sửa tay giới hạn vào điểm ghép route, cấu hình/proxy và Placement khi có bằng chứng.
- Không dùng ours/theirs cho toàn bộ router; không reset --hard, force-push hoặc xóa dữ liệu để làm app chạy.
- Không stage, ghi đè hoặc tự loại bỏ sửa đổi application.yaml có sẵn; .env và secret không đưa vào Git.
- Giữ V1/V2 snapshot và attempt cũ; không publish DRAFT, không replace/reimport toàn bộ samples.
- Tạo bằng chứng mới; số test trước đây chỉ là baseline, không phải kết quả cho bản merge mới.
- Review/merge vào main là bước chia sẻ riêng sau báo cáo; không tự động đẩy main.

## Review Focus

1. URL /placement chỉ có một owner; preview/public layout của main vẫn hoạt động.
2. Login/register giữ pathname/search/hash return-to cho Placement và các module khác.
3. Media V2 cùng origin qua /api; V1 và attempt đang làm vẫn dùng đúng snapshot cũ.
4. .env, Mongo ports và Docker volume của một thành viên không bị áp sang máy khác.
5. Regression xuất hiện do merge phải phân biệt với lỗi đã có trên main và lỗi thiếu hạ tầng Speaking.

### Task 1: Chốt baseline và bảo toàn trạng thái

**Files:** Đọc AGENTS.md hai repo, application.yaml, docs/technical/placement-local-runtime.md; chỉ ghi log/backup ngoài các file nguồn và không chứa secret trong báo cáo Git.
**Interfaces:** Consumes hai SHA HEAD/main mới fetch; produces danh sách thay đổi local và điểm quay lại của hai repo.

- [ ] Chạy git status --short --branch, git fetch origin, git rev-list --left-right --count HEAD...origin/main ở từng repo.
- [ ] Ghi SHA chính xác, lưu riêng diff application.yaml và xác nhận file đó không bị đưa vào phạm vi commit.
- [ ] Tạo nhánh/checkout tích hợp mới từ HEAD hiện tại để thử merge. Ưu tiên worktree để checkout đang dùng và server đang mở không bị ảnh hưởng.
- [ ] Chạy baseline FE build/lint và ghi các lỗi/cảnh báo; dùng bằng chứng BE baseline đã ghi và chạy lại phần liên quan khi cấu hình/input thay đổi.
**Expected:** Không mất thay đổi local; có baseline trước merge và đường rollback về đúng SHA cũ.

### Task 2: Merge main FE và ghép route đúng trách nhiệm

**Files:** FE src/router.tsx, src/features/landing-page/routes.ts; đọc src/features/topic/routes.ts, src/shared/constants/app.constants.ts, vite.config.ts; docs/features/landing-page/README.md.
**Interfaces:** Consumes route groups mới của main; produces appRoutes có một /placement, đủ preview/public/learner/management/standalone groups.

- [ ] Fetch lại tại thời điểm merge, đóng băng SHA main được tích hợp; merge --no-commit trong checkout riêng.
- [ ] Ghi nhận conflict thực tế; giữ PublicResourceLayout và TopicDetailRoutes/MyTopicsRoutes từ main.
- [ ] Trong LandingPageRoutes giữ LearningPathPreviewView; loại riêng route placeholder /placement và import placeholder không còn dùng.
- [ ] Đăng ký LandingPageRoutes trở lại router cho preview; giữ PlacementRoutes ở ngoài learner shell vì Placement có shell riêng.
- [ ] Giữ GrammarAdminRoutes và ADMIN_GRAMMAR từ nhánh tích hợp; nhận thêm Speaking constants của main.
- [ ] Giữ /ws proxy và các cấu hình mới từ main; không hard-code backend host riêng của máy tác giả vào code chung.
- [ ] Kiểm tra diff: không có thay đổi nghiệp vụ thủ công trong module của thành viên khác. Cập nhật tài liệu route đúng phần ghép.
- [ ] Chạy npm run build và npm run lint. Nếu sửa route/behavior để giải quyết lỗi, bổ sung kiểm tra hồi quy có ý nghĩa trước khi sửa.
**Expected:** Compile thành công; không có route trùng hoặc import/export cũ; giữ đủ màn hình main.

### Task 3: Đối chiếu BE và xác nhận môi trường local

**Files:** BE tools/placement/setup-local.ps1, run-local.ps1, application-placement-local.yaml, .env.example; giữ nguyên application.yaml của người dùng.
**Interfaces:** Consumes BE current main ancestry và cấu hình local; produces BE8080 với Mongo Placement, cookie auth, transaction manager và media.

- [ ] BE behind0 thì không merge thừa. Nếu fetch mới cho thấy main tiến thêm, dừng để đọc diff và mở rộng danh sách điểm tích hợp trước khi merge.
- [ ] Kiểm tra Docker/Mongo replica set, port, app database và app credential thực tế; không in secret.
- [ ] Chạy setup preflight; so sánh ID/nội dung hiện có. Chỉ Apply khi thiếu và nội dung không xung đột; không động tới attempts.
- [ ] Khởi động bằng run-local.ps1; xác nhận dev,placement-local và endpoint media. Không chuyển profile DEV thành mặc định cho toàn bộ test.
- [ ] Kiểm tra FE proxy /api và /ws. Thiếu service Speaking thì ghi rõ giới hạn, không sửa giả response hoặc identity.
**Expected:** Môi trường tác giả chạy được và không phụ thuộc thay đổi YAML riêng để thành viên khác khởi động.

### Task 4: Hồi quy Placement bằng hệ thống thật

**Files:** FE features/placement, auth-return-to tests; BE placement tests. Chỉ sửa sau khi tái hiện và xác định nguyên nhân.
**Interfaces:** Consumes FE sau merge + BE local; produces bảng scenario/request/result và screenshot UI.

- [ ] Guest mở landing -> Placement -> start; lưu đáp án, reload/resume và nộp bài.
- [ ] Trước auth: không lộ score/level; browser khác không truy cập được attempt.
- [ ] Từ submitted: login và register giữ return-to; sai mật khẩu ở lại form; thành công quay đúng submitted rồi claim.
- [ ] Kiểm tra claim lặp, tài khoản đã có kết quả, mất cookie và active-attempt conflict. Hết hạn dùng kiểm tra BE phù hợp; không sửa đồng hồ hệ thống hoặc giả kết quả.
- [ ] Result/review đúng attempt và đáp án đã lưu; logout không đọc được kết quả riêng tư.
- [ ] V2: HEAD/GET/Range/checksum cho sáu WAV qua FE proxy; phát thật trên browser; kiểm tra không dùng server8892.
- [ ] V1: không sửa snapshot cũ; kiểm tra định danh/phiên bản được giữ và ghi nhận phụ thuộc legacy8892 nếu có bài cũ.
- [ ] Chạy test Placement/return-to; fix theo RED -> GREEN. Không dùng mock/fixture làm bằng chứng tích hợp.
**Expected:** Các scenario cốt lõi pass trên code sau merge, không chỉ dựa vào kết quả 53 kiểm tra trước đó.

### Task 5: Smoke test phần dùng chung của nhóm

**Files:** Chỉ đọc và vận hành route của main; không tự sửa logic module khác.
**Interfaces:** Consumes app đã tích hợp; produces ma trận Guest/USER/management routes và phát hiện regression.

- [ ] Guest landing, public Topic/Grammar, Learning Path preview tải đúng và không bị Placement chiếm route.
- [ ] Đăng nhập -> learner home/My Topics/Learning Path/Subscription; return-to từ route bảo vệ không bị phá.
- [ ] Kiểm tra đường vào Assessment và Speaking, /ws proxy và điều hướng qua lại. Chức năng yêu cầu service/dữ liệu riêng chỉ đánh dấu pass khi có backend tương ứng thật.
- [ ] Kiểm tra management routes hiện có theo account được phép; không tạo account admin giả để chứng minh phân quyền.
- [ ] Nếu phát hiện lỗi module khác: ghi request, mã lỗi, phạm vi, so sánh main; sửa riêng phần tích hợp của mình nếu là nguyên nhân, còn lỗi có sẵn thì chuyển thành issue riêng.
**Expected:** Không phát hiện regression mới tại các điểm ghép đã kiểm tra; không tuyên bố bảo đảm mọi module khi thiếu hạ tầng/dữ liệu.

### Task 6: Chứng minh thành viên khác chạy lại được

**Files:** Bản xuất/checkout sạch của đúng cặp commit; không copy .env, node_modules, target hay database của tác giả.
**Interfaces:** Consumes hướng dẫn setup và files tracked; produces bằng chứng clean-room reproducibility.

- [ ] Xuất code sạch ở thư mục tên khác và không có data/bank-320 bên ngoài.
- [ ] Tạo .env từ example, JWT riêng; Mongo replica set/database/port tạm riêng, không dùng volume hay dữ liệu đang chạy của nhóm.
- [ ] Package --check, setup preflight, Apply và Apply lặp; khác nội dung phải bị từ chối trước ghi.
- [ ] Chạy FE/BE từ bản sạch, kiểm tra Guest/Auth/claim/media và các public route sau merge.
- [ ] Dừng riêng dịch vụ/database test do tác vụ tạo; giữ dịch vụ của người dùng.
**Expected:** Người khác có thể làm theo hướng dẫn với đúng branch/cặp commit, không cần file riêng trên D: của tác giả.

### Task 7: Xác minh cuối, review, commit và báo cáo

**Files:** FE docs/features/landing-page/README.md; BE docs/technical/placement-main-refresh-report.md và local-runtime guide khi thay đổi lệnh chạy.
**Interfaces:** Consumes toàn bộ bằng chứng; produces commit pair, báo cáo và nhánh để nhóm review.

- [ ] FE build/lint; BE mvnw.cmd verify. Chỉ chạy thêm kiểm tra Topic API nếu thực sự sửa boundary/contract đó, theo AGENTS.
- [ ] Đối chiếu lỗi full suite theo test identity với main. Baseline cũ: BE7 failure/48 error; số lượng hiện tại phải đọc từ run mới, không coi baseline là pass.
- [ ] Review riêng diff sửa tay ở shared/router/proxy; review lại nghiệp vụ Placement và các bước import.
- [ ] Commit thay đổi có phạm vi rõ; không kèm application.yaml có sẵn của người dùng hoặc .env.
- [ ] Fetch cuối và kiểm tra branch chứa SHA main đã chọn; nếu main vừa tiến thêm, báo khoảng lệch mới và đánh giá trước khi merge tiếp.
- [ ] Push nhánh tích hợp đã kiểm chứng để nhóm review; không push main hay force-push.
- [ ] Hoàn tất báo cáo: SHAs BE/FE/main, file sửa tay, dữ liệu đã/không ghi, scenario pass/fail/unrun, test output, giới hạn và cách rollback.
**Expected:** Có bằng chứng kiểm tra cụ thể; phần chưa kiểm chứng/không thuộc Placement được nêu riêng.

## Rollback và điều kiện dừng

- Merge chưa commit: abort trong checkout tích hợp, không reset checkout có sửa đổi của người dùng.
- Sau commit: dùng revert có phạm vi trên nhánh tích hợp nếu cần; không sửa lịch sử main đã chia sẻ.
- Dữ liệu: không xóa DB/volume để rollback; giữ V1/V2, chỉ cleanup database test đã tạo và xác minh tên/đường dẫn.
- Dừng cập nhật khi gặp khác biệt nội dung dữ liệu, thay đổi contract ảnh hưởng module khác, hoặc yêu cầu sửa nghiệp vụ ngoài Placement; ghi rõ bằng chứng và phương án thay vì mở rộng sửa ngầm.

## Báo cáo kết thúc dự kiến

1. Cặp commit BE/FE đã tích hợp với main SHA nào; ahead/behind sau fetch cuối.
2. Các file conflict đã resolve và lý do lựa chọn.
3. Kết quả Placement, smoke nhóm, clean-clone, audio và full suite.
4. Những lỗi có sẵn, giới hạn do hạ tầng, và các việc chưa thực hiện.
5. Lệnh chạy cho máy hiện tại và thành viên mới; nhánh remote để review.

## Execution record (2026-11-08)
Implemented core merge, runtime, clean-source import, HTTP and public UI verification. See docs/technical/placement-main-refresh-report.md for exact pass/fail/unrun scope; unchecked scenario items are not blanket completion claims. FE commit bf5a13d; BE source852c556. Full BE verify still has baseline55 failures/errors. application.yaml remains excluded.
