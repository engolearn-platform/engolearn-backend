# Placement Test — giải thích ngắn để ôn vấn đáp

Cập nhật 09/11/2026. Áp dụng cho BE `merge/placement-guest-user-main` và FE `fix/placement-guest-navbar` đã bàn giao.

## 1. Chức năng của em làm gì?

Placement Test là bài kiểm tra đầu vào gồm **20 câu trắc nghiệm, 15 phút, Reading và Listening**, để đề xuất level A1–B2. Guest được làm trước khi đăng nhập; muốn xem kết quả phải đăng nhập hoặc đăng ký và nhận bài về tài khoản.

**Luồng tổng quát:** Người dùng thao tác → FE gọi API → BE kiểm tra và xử lý → Mongo lưu dữ liệu → BE trả kết quả → FE hiển thị.

| Thành phần | Trách nhiệm dễ nhớ |
|---|---|
| FE — React/TypeScript | Hiển thị câu hỏi, phát âm thanh, đồng hồ, gửi đáp án, màn kết quả |
| BE — Spring Boot/Java | Kiểm tra chủ bài, hạn giờ, lưu đáp án, chấm điểm, nhận bài Guest và đồng bộ level |
| MongoDB | Lưu câu hỏi, bộ đề, bài làm và hồ sơ người dùng |

**FE không tự quyết định điểm hoặc level; BE mới là nơi quyết định.**

## 2. Data đang lưu ở đâu? Có trên Git chưa?

Có hai loại cần phân biệt:

| Loại | Vị trí trong BE | Ý nghĩa |
|---|---|---|
| Dữ liệu nguồn | `tools/placement/data/seed/` | 320 câu, schema và thông tin nguồn để tạo/kiểm tra gói đề |
| Manifest DEV | `tools/placement/data/dev-v3/` | Danh sách chọn câu, đường dẫn âm thanh, checksum và ghi chú kiểm tra |
| File import | `tools/mongo-json-importer/samples/placement-v3-questions.importer.json` | Đóng gói 320 câu để nạp vào Mongo |
| Bộ đề import hiện tại | `tools/mongo-json-importer/samples/placement-v3-test-sets-v2.importer.json` | Một bộ V2 DRAFT gồm 20 câu: 14 Reading + 6 Listening |
| Âm thanh thật | `tools/mongo-json-importer/samples/placement-media/` | 96 file WAV; một bộ đề sử dụng các file tương ứng của nó |

Các file này **đã trên Git**. Không còn thư mục `data` ở gốc BE. Seed và file importer khác định dạng/vai trò, không phải hai bộ cần import hai lần. File V1 vẫn giữ để tương thích lịch sử; setup hiện tại dùng V2.

Khi chạy, BE đọc/ghi **Mongo**, không đọc JSON để thay database mỗi lần làm bài:

- `placement_questions`: ngân hàng câu hỏi.
- `placement_test_sets`: bộ đề có ID, phiên bản và snapshot nội dung câu hỏi.
- `placement_attempts`: mỗi lần làm bài, chủ bài, hạn giờ, đáp án, trạng thái và kết quả.
- `user_profiles.cefr_level`: level hồ sơ, thuộc module User.

**Git chia sẻ đề và WAV; không chia sẻ database đang chạy của em.** Máy khác cần import vào Mongo của họ. Tài khoản, bài đã làm và kết quả local không tự được push lên Git. `.env` cũng là cấu hình riêng, không chia sẻ mật khẩu.

## 3. Chạy project như thế nào?

**Hằng ngày trên máy đã setup:**

1. Mở Docker Desktop, bảo đảm Mongo đang chạy.
2. Mở BE trong IntelliJ → chọn **EngolearnApplication** → Run. Cấu hình Run chọn một lần `Active profiles = dev,placement-local`; working directory là gốc BE. BE dùng cổng **8080**.
3. Tại thư mục FE, chạy `npm run dev`, rồi mở **http://localhost:3000/placement**.

Vite chuyển các yêu cầu `/api` từ FE sang BE. Giữ cùng host `localhost` trong cả phiên để không nhầm cookie. Không chạy thêm BE bằng script khi BE trong IDE đã chiếm 8080.

**Máy mới:** lấy đúng cặp nhánh BE/FE, chuẩn bị JDK 21, Node/npm, Docker. Từ gốc BE chạy:

```powershell
./tools/placement/setup-local.ps1
```

Setup tạo `.env` khi thiếu, khởi động/kiểm tra Mongo. Nếu báo thiếu dữ liệu trong đúng DB DEV thì chạy `./tools/placement/setup-local.ps1 -Apply`. Không import cả thư mục samples và không xóa database để né lỗi. Tại FE chạy `npm ci` lần đầu, sau đó Run BE và `npm run dev` như trên.

Profile local bật Placement dùng Mongo, transaction và cho phép bộ DRAFT phục vụ DEV. **DRAFT chưa đồng nghĩa đề chính thức đã được chuẩn hóa CEFR.** Nếu có đề PUBLISHED hợp lệ, BE ưu tiên PUBLISHED trước DRAFT.

## 4. Người dùng làm bài, hệ thống xử lý ra sao?

### Guest — chưa đăng nhập

1. Vào `/placement`, bấm bắt đầu → BE chọn bộ đề, tạo bài làm và hạn giờ. Chỉ mở trang giới thiệu chưa bắt đầu tính giờ.
2. BE cấp cookie `placement_guest_session` để nhận diện Guest; FE không tự tạo danh tính hoặc lưu token Guest trong localStorage.
3. Mỗi lần chọn/bỏ chọn đáp án, FE gửi lưu về BE. BE kiểm tra đúng chủ, đúng câu/đáp án và còn thời gian mới chấp nhận.
4. Nộp bài → BE chấm/lưu kết quả. Guest chỉ thấy yêu cầu đăng nhập, chưa được nhận điểm/level qua API kết quả.
5. Đăng nhập/đăng ký → hệ thống nhận bài Guest về tài khoản (**claim**), kiểm tra cookie và quyền sở hữu, rồi mới cho xem kết quả/review. Thời hạn nhận bài là 2 giờ từ lúc kết thúc.
6. Nếu tài khoản đã có kết quả hợp lệ, hệ thống ưu tiên kết quả tài khoản đang có, không dùng bài Guest ghi đè.

### User — đã đăng nhập

User làm và xem kết quả trực tiếp, không qua bước nhận bài Guest. Rời trang/quay lại thì FE gọi `/current` để đọc tiến độ và hiện **Tiếp tục bài làm**. Tiếp tục dùng bài cũ, đáp án đã lưu và deadline cũ, không được cấp lại 15 phút. User đã có kết quả hợp lệ được xem lại, không tự tạo bài mới.

### Hết giờ và mất mạng

BE lưu **deadline cụ thể**; đồng hồ FE chỉ hiển thị theo giờ server. FE gọi nộp khi hết giờ; mỗi lần xử lý yêu cầu liên quan BE cũng kiểm tra deadline, nên đóng tab không làm thời gian dừng. Không cần nói rằng BE có một cron riêng chạy chấm từng bài.

| Trường hợp | Xử lý |
|---|---|
| Nộp tay khi chưa trả lời câu nào | Từ chối, yêu cầu trả lời trước |
| Hết giờ, có đáp án đã lưu | `AUTO_SUBMITTED`: tự chốt/chấm các đáp án đã lưu |
| Hết giờ, chưa có đáp án | `EXPIRED`: không tạo kết quả đánh giá |
| Đáp án lưu lỗi khi còn giờ | Báo lỗi, không âm thầm nộp bỏ qua đáp án chưa lưu |
| Lưu trễ nhưng BE đã chốt bài | FE đọc trạng thái thật rồi nhận kết quả qua API nộp, tránh kẹt màn làm bài |

Đáp án chưa được BE xác nhận không được bảo đảm đã lưu. Mất mạng không được kéo dài deadline.

## 5. Chấm điểm, cập nhật level và Listening

BE đối chiếu đáp án đã lưu với đáp án đúng trong **đúng ID/phiên bản bộ đề được gán**, câu đúng được tính vào tổng số đúng; câu sai/bỏ trống không tăng điểm.

| Số câu đúng / 20 | Level theo quy tắc hiện tại |
|---|---|
| 0–5 | A1 |
| 6–10 | A2 |
| 11–15 | B1 |
| 16–20 | B2 |

Đây là ngưỡng của ứng dụng, không nên trình bày như chứng chỉ CEFR được công nhận. Bài EXPIRED không được gán kết quả A1 chỉ vì đúng 0 câu.

Đăng ký mới mặc định A1. User hoàn tất bài hoặc nhận bài Guest thành công sẽ cập nhật hồ sơ theo **level cao hơn giữa hồ sơ hiện tại và Placement**: A1 + kết quả B1 → B1; B2 + kết quả A2 → giữ B2. C1/C2 cũng không bị hạ. Kết quả và cập nhật level cùng transaction: lỗi thì không lưu dở một nửa. Module User sở hữu cập nhật hồ sơ; Chủ đề vẫn đọc level như luồng cũ.

**Bắt đầu học** gọi API xác nhận/đồng bộ rồi mới đến `/topics`; kết quả cũ có thể được đồng bộ qua nút này. Review hiện đáp án/giải thích khi đúng chủ bài và câu có explanation. API đang thi không trả đáp án đúng hoặc giải thích.

Listening V2 dùng `/api/v1/placement/media/<hash>.wav`: trình duyệt → proxy FE → BE trả WAV từ samples. Hash định danh nội dung; BE hỗ trợ tải từng đoạn (Range). V2 không cần bật server audio Node riêng. Bài V1 lịch sử có thể còn URL `localhost:8892`, không tự sửa snapshot cũ thành V2.

## 6. Các file và API nên nhớ

| File/nhóm | Khi giảng viên hỏi |
|---|---|
| [FE routes](../../../engolearn-frontend/src/features/placement/routes.ts) | Các màn giới thiệu, làm bài, nhận bài, kết quả, review |
| [FE usePlacementAttempt](../../../engolearn-frontend/src/features/placement/hooks/usePlacementAttempt.ts) | Đáp án đang chọn, hàng đợi lưu, nộp bài và xử lý lưu trễ |
| [PlacementAttemptController](../../src/main/java/fit/iuh/engolearn/placement/controller/PlacementAttemptController.java) | Nhận yêu cầu HTTP, cookie và người đang đăng nhập |
| [PlacementAttemptServiceImpl](../../src/main/java/fit/iuh/engolearn/placement/service/impl/PlacementAttemptServiceImpl.java) | Bắt đầu/tiếp tục bài, kiểm tra chủ bài, lưu đáp án, nộp |
| [PlacementDeadlineServiceImpl](../../src/main/java/fit/iuh/engolearn/placement/service/impl/PlacementDeadlineServiceImpl.java) | Hết giờ, chấm số đúng và quy đổi level |
| [PlacementLevelSyncDeadlineServiceImpl](../../src/main/java/fit/iuh/engolearn/placement/service/impl/PlacementLevelSyncDeadlineServiceImpl.java) | Ghép transaction/cập nhật level, tránh nộp trên revision đã lỗi thời |

Nhóm API gốc `/api/v1/placement/attempts`: POST để bắt đầu; GET `/current` để tìm bài hiện có; GET `/{id}` để đọc bài; PATCH `/{id}/answers` để lưu; POST `/{id}/submit` để nộp; GET `/{id}/result` và `/review` để xem; POST `/{id}/learning` trước khi vào học. **Controller nhận → Service xử lý → Repository truy cập Mongo.**

## 7. Câu hỏi vấn đáp dễ gặp

**Vì sao chấm ở BE?** Để người dùng không tự sửa điểm/level trên trình duyệt; BE kiểm tra cả quyền sở hữu và deadline.

**Vì sao có snapshot/phiên bản đề?** Để bài đã gán được chấm và xem lại theo đúng nội dung lúc thi, không bị đề mới làm đổi kết quả cũ.

**Hai tab cùng nộp có sao không?** Revision kiểm tra phiên bản bài, transaction bảo đảm lưu đồng bộ. Nếu đáp án đổi sau khi kiểm tra, phải đọc/kiểm tra lại; không chấm dựa trên lần kiểm tra đã cũ. Gọi nộp lại bài đã chốt trả trạng thái cuối.

**Làm sao tránh ảnh hưởng module khác?** Placement gửi level qua contract của User; không tự ghi đè toàn bộ hồ sơ, không sửa nghiệp vụ Chủ đề. Navbar/sidebar dùng thành phần chung. Tích hợp phải lấy đúng cặp nhánh BE và FE.

**Đã kiểm thử hết app chưa?** Placement hiện có 141 test pass/1 skip; 79 kiểm tra FE→BE→Mongo thật pass; FE build pass. Toàn BE còn 55 trường hợp lỗi nền ngoài Placement đã được báo cáo. Không khẳng định toàn ứng dụng không còn lỗi hoặc đề đã chuẩn hóa CEFR.

## 8. Đoạn trình bày mẫu khoảng một phút

> Em xây dựng chức năng kiểm tra đầu vào gồm 20 câu Reading và Listening trong 15 phút. Guest được làm trước, nhưng phải đăng nhập để nhận bài và xem kết quả. FE hiển thị, phát âm thanh và gửi lựa chọn; BE kiểm tra chủ bài, deadline, lưu đáp án và chấm điểm. Mongo lưu đề theo phiên bản và lưu từng bài làm, nên người dùng có thể tiếp tục mà không được reset thời gian. Khi hoàn tất, hệ thống xác định A1–B2 và chỉ nâng hoặc giữ nguyên level hồ sơ thông qua module User. Nút Bắt đầu học chuyển sang Chủ đề sau khi đồng bộ thành công. Dữ liệu đề và WAV đã được chia sẻ trên Git; mỗi máy import vào Mongo riêng. Luồng có kiểm thử tích hợp thật, còn các lỗi nền của toàn dự án được ghi rõ khi bàn giao.

Nếu cần thao tác setup chi tiết, xem [hướng dẫn chạy](placement-local-runtime.md) và [báo cáo bàn giao](placement-prepush-audit.md). File ôn này chỉ giải thích hiện trạng, không thay đổi code/data và chưa được push trong lượt viết tài liệu.
