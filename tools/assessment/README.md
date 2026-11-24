# Chuẩn bị dữ liệu Đánh giá định kỳ cho bản demo

## Chạy Placement → Đánh giá định kỳ trên máy thử

Cần Git, JDK 21, Node 22 từ 22.18, npm, Python 3.9+, curl, OpenSSL và
Docker chạy Linux containers. Dùng terminal Ubuntu hoặc terminal của distro WSL2.
Trên Windows, kiểm tra cột `VERSION` của distro bằng `wsl -l -v` trong PowerShell.
Trong Docker Desktop, chọn **Linux containers**, dùng **WSL 2 based engine** và bật
**Settings → Resources → WSL Integration** cho distro đó. Mở terminal WSL2,
kiểm tra các công cụ bên trong distro và chạy `docker info` trước khi khởi động demo.
Xem [hướng dẫn Docker Desktop và WSL2](https://docs.docker.com/desktop/features/wsl/).

Kiểm tra Docker trong terminal sẽ chạy demo:

```bash
docker info --format '{{.ServerVersion}}'
docker context inspect --format '{{.Endpoints.docker.Host}}' "$(docker context show)"
javac -version
node --version
npm --version
python3 --version
```

Script cần endpoint Docker cục bộ bắt đầu bằng `unix://`. Nếu Docker chỉ chạy
được trong PowerShell, kiểm lại WSL Integration và context trong terminal WSL2.

Từ một thư mục mới, clone cùng nhánh bàn giao ở cả hai repo rồi chạy từ backend:

```bash
git clone -b feat/pfc-placement-integration https://github.com/engolearn-platform/engolearn-backend.git
git clone -b feat/pfc-placement-integration https://github.com/engolearn-platform/engolearn-frontend.git
cd engolearn-backend
bash tools/assessment/run-placement-demo.sh ../engolearn-frontend
```

Nếu checkout frontend nằm nơi khác, truyền đường dẫn thực. Chương trình tạo MongoDB
8 replica set mới trên loopback và nạp đúng một đề Placement đã xuất bản theo Form 01
với WAV dự phòng đóng gói, một đề định kỳ demo v2 và catalog cần thiết. Mỗi lần chạy
dùng DB mới. Hai ứng dụng được mở từ mã của từng checkout. Không cần dùng lại kết
quả đã có hoặc nạp baseline giả. Giữ terminal mở, mở URL in ra và đăng ký học viên
mới. Dừng bằng Ctrl+C. Ba cổng mặc định là 28380 (BE), 28300 (FE) và 28317 (Mongo).
Đổi bằng biến `PFC_BACKEND_PORT`, `PFC_FRONTEND_PORT` và `PFC_MONGO_PORT` khi cần.

Với cổng mặc định, mở [Placement demo](http://localhost:28300/placement).
Khi chạy trong WSL2, mở địa chỉ đó từ trình duyệt Windows. Demo local dùng WAV
mẫu đi kèm và không cần tài khoản Cloudinary hay MongoDB có sẵn.
Xem [cách Windows truy cập ứng dụng trong WSL](https://learn.microsoft.com/windows/wsl/networking).

1. Mở `/assessment` sau khi đăng nhập. Mục điều kiện dẫn tới `/placement` nếu tài
   khoản chưa có kết quả.
2. Làm và nộp Placement bằng cùng tài khoản. Nhấp **Đến đánh giá định kỳ** ở trang kết quả.
3. Xác nhận có kết quả đầu vào hợp lệ, có đề định kỳ đã xuất bản và còn lượt.
   Nghe thử, xác nhận, bắt đầu, lưu đáp án, nộp và mở kết quả/lịch sử.
4. Mở lại `/assessment` để kiểm mốc chờ 21 ngày. Đăng nhập tài khoản thử thứ hai
   để kiểm không mở được kết quả của tài khoản đầu.

Nếu dùng DB dev nhóm thay cho demo, tự nạp/publish đề định kỳ riêng theo hướng dẫn
Assessment. Placement, đề định kỳ, media và catalog đều phải nằm trong môi trường
mà backend đang chạy và tại DB `APP_DB` đã cấu hình. Dùng profile `prod` với đề
chính thức và Media được duyệt theo phần dưới khi nghiệm thu môi trường nhóm.

## Tách nguồn dữ liệu

Dùng profile `dev` để soạn và thử bộ WAV mẫu. Bản demo chung dùng profile `prod`,
HTTPS và cơ sở dữ liệu demo riêng. Mỗi bản chạy dùng đúng một profile. Xem
[chuẩn runtime](../../docs/architecture/runtime-configuration.md) và
[hướng dẫn Media](../../docs/technical/media/integration-guide.md).

Chuẩn bị đề phiên bản 2 và WAV có quyền sử dụng cho demo. Giữ JSON tên trường
snake_case từ công cụ nhập dữ liệu. Bộ `assessment-demo.json` trong repo có cờ
`demonstration: true` và chỉ dùng để kiểm tra. Bản demo dùng đề đã được nhóm duyệt.

## Kiểm tệp và upload

Chạy từ backend. Thay đường dẫn trong lệnh bằng gói dữ liệu đã được duyệt. Lệnh này
chỉ đọc tệp, kiểm dung lượng, PCM, thời lượng và SHA-256 của toàn bộ audio trước upload.

```bash
python3 tools/assessment/migrate-audio.py /duong/dan/de-da-duyet.json \
  --media-dir /duong/dan/wav --sound-check sound-check.wav \
  --draft-series PFC-DEMO-01 --draft-name 'Đề demo Đánh giá định kỳ'
```

Sau khi kiểm thành công, thêm `--apply --base-url https://demo.example.org --email EMAIL`.
Script hỏi mật khẩu của tài khoản `CONTENT_MANAGER` hoặc `ADMIN` trực tiếp trong terminal.
URL demo phải là origin chuyển `/api` vào backend đang trỏ tới database/Cloudinary demo.
Kiểm đúng đích trước khi chạy. Giữ khóa Cloudinary mới trong môi trường backend.
Script dùng cookie phiên, còn backend mới gọi Media. Không đưa khóa vào lệnh hoặc JSON.

Với dữ liệu dài hạn, cung cấp thêm các manifest có `collections.assessment_attempts` chứa
`paper` snapshot và WAV gốc. Bỏ các cờ `--draft-series` và `--draft-name` để chỉ đăng ký
audio cũ. Bỏ `--sound-check` khi muốn giữ cấu hình âm thử hiện có. Ưu tiên bản xuất từ database
đã được phép dùng, vì dữ liệu tác giả chỉ chứa đề có thể không bao phủ lịch sử. Script không
thay đổi tài liệu lịch sử. Tất cả dữ liệu Mongo cần tiếp tục trỏ đến đúng các bản ghi
`media_assets` của cùng môi trường.

Upload lưu một tham chiếu bất biến theo SHA-256. Chạy lại với cùng tham số để tiếp tục sau
lỗi mạng hoặc thao tác dở. Nếu hệ thống báo `AUDIO_UPLOAD_PENDING`, chờ yêu cầu trước kết thúc
hoặc hết thời hạn giữ chỗ 15 phút rồi thử lại. Nếu tiến trình chết sau khi Cloudinary đã nhận
file nhưng trước khi lưu tham chiếu Assessment, có thể còn asset mồ côi. Chỉ dọn sau khi đã
kiểm tra cả đề, nháp, lịch sử và metadata Media. API Media không cung cấp xóa file công khai.
Nếu nháp đích đã có nội dung khác, script giữ nguyên nội dung và dừng thay vì ghi đè.

## Xuất bản và kiểm tra trên trình duyệt

1. Mở `/admin/assessment` bằng tài khoản quản lý nội dung. Mở nháp, bấm **Kiểm tra và xem thử**,
   nghe từng WAV, kiểm đáp án và lời giải. Xác nhận kiểm tra rồi xuất bản. Nếu sửa nháp,
   lưu lại và duyệt revision mới.
2. Đăng nhập học viên có **Placement đã hoàn thành thuộc đúng tài khoản** và còn quota.
   Trong `/assessment`, xác nhận có đề, baseline và quota. Nghe thử rồi làm đủ Nghe và Đọc.
3. Mở lịch sử, kết quả, chi tiết. Nghe và tua audio xem lại. Sau thời hạn URL, thử tải lại
   và kiểm tra URL mới hoạt động. Kiểm tra trình duyệt tải WAV, phát trực tiếp và Range
   theo [runbook Media](../../docs/technical/media/testing-and-operations.md).
4. Kiểm tài khoản khác không xem được lượt này. Kiểm guest nhận 401, học viên nhận 403 ở API
   quản trị, quản lý nội dung và admin thao tác được. Xác nhận xuất bản hoặc ngừng đề chỉ
   ảnh hưởng lượt mới. Nếu bản demo kiểm luồng Learning Path, đối chiếu năm pool thực trong DB.
5. Chạy JAR từ thư mục ngoài repo, với đúng môi trường demo và không truyền
   `ASSESSMENT_MEDIA_DIRECTORY`. Lặp lại ít nhất nghe thử, bắt đầu và nghe lại để loại trừ
   phụ thuộc vào `tools/` hay thư mục làm việc. Ghi giá trị SHA, phiên bản test set, thời điểm
   và kết quả. Tránh lưu URL ký sẵn trong ảnh hoặc log bàn giao.

Nếu prod chưa có bản ghi Placement thực cho học viên, hoàn tất Placement trên môi trường
được phép hoặc dùng tài khoản demo đã có kết quả hợp lệ. Việc chuyển file audio của
Placement sang Cloudinary là công việc của module Placement.

## Kiểm thử backend

```bash
./mvnw -B -ntp -Dtest=AssessmentDraftIntegrationTest,AssessmentMediaTest,AssessmentRulesTest,AssessmentVersionTwoTest,RuntimeProfilesTest,ArchitectureTest test
./mvnw -B -ntp clean verify
```

`AssessmentDraftIntegrationTest` tự dùng Testcontainers khi có Docker. Có thể chỉ định
`ASSESSMENT_DRAFT_TEST_MONGO_URI` để dùng replica set thử nghiệm riêng. Đặt
`ASSESSMENT_TEST_MONGO_URI` cho `AssessmentMongoIntegrationTest`. Hai suite này xóa dữ liệu
thử trong các database cố định `engolearn_assessment_draft_it` và `engolearn_pfc_test`.
Chỉ trỏ tới replica set mới dành riêng cho test. Kiểm tra số ca chạy và skip trong
`target/surefire-reports`. CI mặc định dùng `prod` với cấu hình thử như trong runbook và
các bộ test chỉ định `dev` vẫn dùng profile riêng của mình.
