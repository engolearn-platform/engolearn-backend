# Kho câu hỏi A1

Ngày dựng bộ: 28/11/2026. Phạm vi được duyệt: bổ sung 300 câu A1 cho 10 micro-skill,
passage và audio mới; upload bằng Media hiện có. Không sửa Assessment hoặc mapping.

## Nội dung và trạng thái

- 30 passage Listening và 30 passage Reading, mỗi passage có 5 câu.
- Mỗi canonical micro-skill có đúng 30 câu; tổng 150 Listening và 150 Reading.
- Chủ đề gia đình, trường học, bạn bè, mua sắm, đi lại, thông báo và sinh hoạt hằng ngày.
- Bốn lựa chọn A–D, một đáp án đúng, giải thích tiếng Việt và bằng chứng từ nguồn.
- Mỗi vị trí đáp án đúng xuất hiện 75 lần. ID câu hỏi theo `LP-Q-*-A1-V1-*`.
- Tất cả passage/câu hỏi là **DRAFT**, chưa có xác nhận duyệt chuyên môn hoặc nghe
  audio. Không tự thêm publication_review và không đổi ACTIVE để vượt readiness.
- Bộ này chưa được hiệu chuẩn CEFR qua người học. Cần người duyệt kiểm tra mức A1,
  suy luận trực tiếp, độ hợp lý của phương án nhiễu và đáp án đúng nhất.

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
sang cloud khác. Xem [hướng dẫn và công cụ A1](../../../tools/learning-path-a1/README.md).

Review toàn bộ nội dung tại [content-review.md](../../../tools/learning-path-a1/content-review.md)
và [manifest](../../../tools/learning-path-a1/review-manifest.json). Trong admin, lọc
level A1 để nghe/xem passage; tiêu đề mô tả nội dung, không có prefix level, giống bộ B1. Sau khi duyệt, xuất bản passage
trước rồi câu hỏi bằng luồng admin hiện có. Cần 30 câu ACTIVE/canonical/level để
đủ điều kiện tạo path; DRAFT không được tính vào số này. Catalog có 10 kỹ năng
không tự mở rộng 5 mapping Assessment đã được duyệt.

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
thử vì dữ liệu còn DRAFT chờ duyệt; không khẳng định có thể tạo path A1 từ bộ chưa
xuất bản. Để kiểm tra tạo path/practice/review, cần duyệt và xuất bản đủ 30 câu của
canonical A1 tương ứng, dùng Assessment READY có mapping đã được duyệt.

Biên nhận upload, bản export, audio local/tải lại và báo cáo kiểm tra nằm trong
`.artifacts/learning-path-a1/` (gitignore); không chứa cookie hoặc signed URL đã lưu.

## Cập nhật tiêu đề

Đã bỏ prefix `A1 |` khỏi đúng 60 passage A1-V1 qua API PATCH có kiểm tra revision.
Giữ nguyên nội dung, trạng thái DRAFT và Media ID; không upload audio lại. Revision
tăng theo luồng sửa bình thường, JSON chia sẻ và công cụ sinh đã đồng bộ. Level
được thể hiện qua trường/bộ lọc A1, không đưa vào title.
