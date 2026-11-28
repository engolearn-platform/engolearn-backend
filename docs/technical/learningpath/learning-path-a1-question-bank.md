# Kho câu hỏi A1

Ngày dựng bộ: 28/11/2026. Phạm vi được duyệt: bổ sung 300 câu A1 cho 10 micro-skill,
passage và audio mới; upload bằng Media hiện có. Không sửa Assessment hoặc mapping.

## Nội dung và trạng thái

- 30 passage Listening và 30 passage Reading, mỗi passage có 5 câu.
- Mỗi canonical micro-skill có đúng 30 câu; tổng 150 Listening và 150 Reading.
- Chủ đề gia đình, trường học, bạn bè, mua sắm, đi lại, thông báo và sinh hoạt hằng ngày.
- Bốn lựa chọn A–D, một đáp án đúng, giải thích tiếng Việt và bằng chứng từ nguồn.
- Mỗi vị trí đáp án đúng xuất hiện 75 lần. ID câu hỏi theo `LP-Q-*-A1-V1-*`.
- Tất cả passage/câu hỏi vẫn là **DRAFT**. Codex đã rà 300/300 câu theo yêu cầu
  chủ dự án: logic cơ bản, từ vựng/cách hỏi A1, đáp án có căn cứ trong nguồn.
  Không tự thêm publication_review hoặc đổi ACTIVE.
- Đây là lượt rà nội dung bằng AI theo mức kiểm tra chủ dự án yêu cầu, không phải
  chứng nhận CEFR hoặc hiệu chuẩn qua người học. Chưa nghe duyệt toàn bộ audio.

## Audio

30 recording mới, Microsoft Zira Desktop (tiếng Anh, Rate -2), MP3 mono 96 kbps,
âm lượng chuẩn hóa bằng FFmpeg. Thời lượng khoảng 20–24 giây/bài.
Audio tổng hợp không phải recording người thật.

Upload bằng tài khoản quản trị demo qua API tạo passage DRAFT; Learning Path gọi
`MediaManagement`, Media quản lý Cloudinary. Không dùng API Cloudinary trực tiếp.
Passage lưu Media ID ổn định, policy PROTECTED; URL phân phối được resolve khi đọc.
Không dùng file local hoặc signed URL cố định làm nguồn runtime.

## Import và duyệt

File chia sẻ duy nhất cho A1:
[`learning-path-a1.json`](../../../tools/mongo-json-importer/samples/learning-path-a1.json).
Ba collection / 390 bản ghi: 60 passage, 300 câu hỏi, 30 Media.
Không có user, Assessment result, path hoặc snapshot learner trong bundle.

Dùng importer với `skip` và `MONGO_IMPORT_OBJECT_ID_FIELDS=none`; ObjectId đã được
biểu diễn bằng Extended JSON. Import lại không ghi đè bản ghi cùng ID. Máy import
phải dùng Cloudinary hiện chứa asset; JSON metadata không tự copy/upload audio
sang cloud khác.

Các thành viên chỉ cần pull file JSON và chạy importer tại root backend:

```powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\learning-path-a1.json -DryRun
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\learning-path-a1.json
```

Trong admin, lọc level A1 để xem/nghe passage; title không có prefix level. Muốn
đưa vào luyện tập phải xuất bản passage trước rồi câu hỏi qua luồng admin hiện có.
Cần 30 câu ACTIVE/canonical/level; DRAFT không được tính. Catalog 10 kỹ năng không
tự mở rộng 5 mapping Assessment đã được duyệt. Không cần các file công cụ biên
soạn hoặc báo cáo kiểm thử nội bộ để import và sử dụng dữ liệu.

## Kiểm chứng

Nguồn và bundle đã kiểm tra: đủ số lượng, canonical đúng nhóm, ID không trùng,
liên kết câu hỏi→passage→Media đầy đủ, bốn lựa chọn khác nhau, vị trí đáp án và trích
dẫn có trong passage. 30 MP3 local giải mã được bằng FFmpeg.

Dry-run importer đạt; import DB local `engolearn_db` bằng `skip`: thêm 30 passage
Reading và 300 câu hỏi; giữ 30 passage Listening/30 Media vừa tạo bằng API. Không
replace collection hoặc nhập lại bundle B1/người học.

Kiểm tra sau import qua HTTP thật tại frontend proxy/backend:

- 60/60 passage đạt readiness cấu trúc, không blocker.
- 30/30 audio Cloudinary tải HTTP 200; Range bytes=2000–3999 trả 206 và đúng bytes.
  Tất cả giải mã được bằng FFmpeg, thời lượng khớp file local trong sai số 0,15 giây.
  Một lượt tải có lỗi bắt tay TLS tạm thời; đã kiểm tra lại những file chưa đạt và
  hoàn tất đủ 30 file, không coi request lỗi là bằng chứng thành công.
- Chi tiết và readiness của 300/300 câu trả đúng canonical, level/trạng thái DRAFT.
  Blocker duy nhất là `PASSAGE_NOT_ACTIVE`, đúng quy tắc phải xuất bản passage trước;
  không có lỗi dữ liệu/phân loại/Media khác. Không tự xác nhận checklist.
- DB: đúng 300 câu DRAFT, 0 câu ACTIVE thuộc bộ A1-V1, mỗi canonical 30 câu.
- Import lại dry-run: 390 existing, 0 new, 0 replace, 0 errors.
- Trình duyệt admin trước khi bỏ prefix: lọc level A1 + từ khóa `A1 |` trả 60 passage; liên kết từ
  passage mẫu trả đúng 5 câu. Chi tiết câu mẫu hiển thị transcript, bốn lựa chọn,
  đáp án đúng và giải thích tiếng Việt. Chưa xác nhận nghe audio qua trình duyệt;
  kiểm tra tải/Range/giải mã ở trên là kiểm tra kỹ thuật provider.

Không chạy bộ Maven toàn hệ thống cho thay đổi dữ liệu này. Luồng learner mới chưa
thử vì dữ liệu còn DRAFT chưa xuất bản; không khẳng định có thể tạo path A1 từ bộ chưa
xuất bản. Để kiểm tra tạo path/practice/review, cần duyệt và xuất bản đủ 30 câu của
canonical A1 tương ứng, dùng Assessment READY có mapping đã được duyệt.

Biên nhận upload, bản export, audio local/tải lại và báo cáo kiểm tra nằm trong
`.artifacts/learning-path-a1/` (gitignore); không chứa cookie hoặc signed URL đã lưu.

## Cập nhật tiêu đề

Đã bỏ prefix `A1 |` khỏi đúng 60 passage A1-V1 qua API PATCH có kiểm tra revision.
Giữ nguyên nội dung, trạng thái DRAFT và Media ID; không upload audio lại. Revision
tăng theo luồng sửa bình thường, JSON chia sẻ và công cụ sinh đã đồng bộ. Level
được thể hiện qua trường/bộ lọc A1, không đưa vào title.

## Rà nội dung theo yêu cầu chủ dự án

Đã đọc và rà đủ 60 passage/300 câu, xét cả nguồn, câu hỏi, đáp án và phương án
nhiễu theo mức kiểm tra đơn giản chủ dự án chốt: hợp logic, ngôn ngữ cơ bản A1,
bằng chứng gần và một lựa chọn được nguồn hỗ trợ rõ nhất. Đã sửa 77 câu và 8
passage Reading; 223 câu còn lại được giữ. Ví dụ thay considerate/valid/suitable
seating bằng cách hỏi quen thuộc hơn, bỏ suy đoán đã mua vé hoặc cần bộ sạc, thêm
ngữ cảnh cho borrow/present/on foot/fresh/entrance/view/weekdays.

Cập nhật DB bằng API có revision, đồng bộ JSON chia sẻ. Giữ ID, canonical, số lượng,
trạng thái DRAFT và 30 Media ID. Transcript Listening không đổi nên không upload
lại audio. Kết quả rà được ghi trong metadata của JSON, không giả thành phiếu duyệt
chuyên môn của người hoặc xác nhận nghe audio. Dữ liệu vẫn chưa ACTIVE.
