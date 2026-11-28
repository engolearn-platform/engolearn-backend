# Kho câu hỏi A1 đang sử dụng

Bộ A1 được dựng và duyệt ngày 28/11/2026 trên nhánh
`feat/learning-path-question-bank`. Phạm vi là dữ liệu kho Learning Path và Media;
không sửa Assessment, mapping recommendation hoặc frontend.

## Nội dung và trạng thái

- **300 câu ACTIVE:** 150 Listening, 150 Reading, đúng 30 câu cho mỗi canonical
  micro-skill trong catalog. Bốn lựa chọn khác nhau, một key, giải thích tiếng
  Việt và bằng chứng từ passage. Vị trí key: 75 A/75 B/75 C/75 D.
- **60 passage ACTIVE:** 30 Listening, 30 Reading, năm câu/passage. Tiêu đề không
  có prefix A1. Question ID theo `LP-Q-*-A1-V1-*`; Listening dùng ID do API cấp.
- **30 audio Cloudinary**, policy PROTECTED, passage tham chiếu bằng
  `audio_media_id`. Không có signed URL hoặc URL local cố định trong bundle.

Chủ đề: gia đình, trường học, bạn bè, mua sắm, đi lại và sinh hoạt hằng ngày.
Nguồn ngắn, từ ngữ cơ bản, bằng chứng gần. Câu suy luận A1 là bài luyện nền tảng
từ tín hiệu trực tiếp theo AGENT.md; chưa phải bộ đã hiệu chuẩn CEFR với người học.

## Rà nội dung và audio

Đã đọc lại đủ 60 passage/300 câu theo AGENT.md §17.1–17.7: đúng mục tiêu kỹ năng,
ngôn ngữ phù hợp mức cơ bản, đáp án có căn cứ và không vượt bằng chứng, các lựa
chọn khác không đồng thời đúng. Câu từ vựng có ngữ cảnh, không đòi kiến thức ngoài
bài. Thái độ/mục đích dựa vào lời thể hiện rõ như xin lỗi, cảm ơn, lời mời, lời
nhờ; không hỏi mỉa mai hay sắc thái giọng tinh tế mà TTS không thể hiện được.

Lượt rà trước đã sửa 77 câu và tám passage Reading. Lượt duyệt xuất bản này sửa
thêm ba câu qua API DRAFT có revision:

- `LP-Q-L-INFERENCE-A1-V1-014`: suy ra kế hoạch đi học cùng nhau **sáng hôm đó**,
  không suy thành thói quen Tom thường xuyên chờ ở góc đường.
- `LP-Q-L-INFERENCE-A1-V1-017`: suy người nói có thể là giáo viên piano từ các
  dấu hiệu về giờ học, phòng và chơi nhạc cùng Jack; thay câu chỉ hỏi lại mong
  muốn nghe bài nhạc được nói trực tiếp.
- `LP-Q-R-SPECIFIC-A1-V1-010`: hỏi giờ kết thúc được nêu rõ, thay phép tính thời
  lượng để giữ đúng kỹ năng tìm dữ kiện.

Giữ ID, canonical, passage, transcript và Media ID; không tạo audio lại.

Audio tổng hợp bằng Microsoft Zira Desktop, Rate -2, MP3 mono 96 kbps, chuẩn hóa
âm lượng; thời lượng khoảng 20–24 giây, khoảng 112–140 từ/phút. Đây là số đo của
recording, không phải ngưỡng CEFR bắt buộc. Đã đối chiếu hash nguồn/file, nhận
dạng faster-whisper, rà các khác biệt với transcript và bằng chứng câu hỏi,
kiểm tra tín hiệu và giải mã. Khác biệt chủ yếu là chính tả Anh/Mỹ, tên riêng,
từ đồng âm hoặc một từ nối; không phát hiện sai khác ảnh hưởng đáp án trong lượt
kiểm tra này.

Đây là **rà nội dung bằng AI và kiểm tra audio tự động**, không tuyên bố người
thật đã nghe duyệt hoặc có hiệu chuẩn thực nghiệm. Nên tiếp tục điều chỉnh theo
phản hồi của người học và người có chuyên môn.

Sau rà duyệt, xuất bản passage trước rồi câu hỏi qua API readiness/publish có
revision, bằng session CONTENT_MANAGER được ủy quyền. Bundle là bản xuất MongoDB
sau publish, có `publication_review` thật; không tự gán ACTIVE để bỏ quy trình.

## Pull và import

File chia sẻ duy nhất:
[`learning-path-a1.json`](../../../tools/mongo-json-importer/samples/learning-path-a1.json).
Ba collection / 390 bản ghi: 60 passage, 300 câu hỏi, 30 Media. Không chứa user,
source Assessment, path hoặc snapshot kiểm thử. Không cần công cụ biên soạn nội bộ.

Import catalog trước. Cấu hình tool-local `.env` đúng DB, conflict policy `skip`,
`MONGO_IMPORT_OBJECT_ID_FIELDS=none`; ObjectId đã dùng Extended JSON:

```powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\learning-path-a1.json -DryRun
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\learning-path-a1.json
```

**Máy đã import bản A1 DRAFT trước đây:** `skip` giữ nguyên bản ghi cũ, không cập
nhật nội dung/trạng thái từ bundle mới. Muốn đồng bộ đúng bản đã duyệt, có thể
đặt `MONGO_IMPORT_CONFLICT_POLICY=replace` trong .env của tool, chạy dry-run rồi
kiểm tra đúng file/DB trước khi import; thao tác này
thay toàn bộ các bản ghi cùng ID của riêng bundle A1, gồm 30 metadata Media. Nó
sẽ ghi đè chỉnh sửa local trên các bản ghi đó. Sau khi import, đổi policy về `skip`.
Không dùng replace cho toàn DB.

Media phải dùng cấu hình Cloudinary chứa các asset; JSON không tự upload/copy
audio sang cloud khác. Trong admin lọc A1 để xem bộ đang sử dụng. Đủ 10 micro-skill
trong kho không tự mở rộng năm mapping Assessment đã được duyệt.

## Kiểm chứng

- 60 passage/300 câu đạt kiểm tra canonical/skill/level, tham chiếu, lựa chọn,
  đáp án và bằng chứng. Trước xuất bản, 300 câu chỉ có blocker dự kiến
  `PASSAGE_NOT_ACTIVE`; đã publish passage trước rồi câu hỏi, có phiếu duyệt thật.
- 30/30 audio qua đối chiếu nguồn/file, ASR và tín hiệu; Cloudinary HTTP 200,
  Range bytes 2000–3999 trả 206 đúng bytes, FFmpeg giải mã được, thời lượng lệch
  file local dưới 0,15 giây. Sai khác ASR đã được rà, không dùng điểm nhận dạng
  để tự chứng nhận CEFR.
- Dry-run bundle cuối: 390 existing, 0 new, 0 replace, không có lỗi.
- Backend/MongoDB riêng: bản sao profile A1 và source READY, không sửa Assessment
  hoặc profile trong DB dùng chung. Recommendation AVAILABLE với năm mapping
  hiện có; tạo path A1 thành công; tạo lại cùng source trả cùng path.
- Có 150 snapshot, trong đó 60 Listening dùng Media ID, không có legacy audio
  URL. Start ngày trả 10 câu A1 Reading/Listening, không lộ đáp án/explanation/
  transcript. Lưu đủ 10 lựa chọn và resume giữ nguyên thứ tự/đáp án.
- Audio/renewal/Range đạt; submit COMPLETED, chấm khớp key; review có đúng lựa
  chọn, đáp án, giải thích và transcript. Guest đọc kết quả bị chặn 401.
- Hash question pool/passage A2/B1/B2 trước/sau khớp. Giữ nguyên ID, title,
  nội dung passage/transcript và 30 metadata Media của bộ A1. Chỉ ba câu nêu trên
  đổi nội dung trong lượt duyệt này.
- Không chạy bộ Maven của module khác cho thay đổi dữ liệu này. Môi trường
  kiểm thử riêng đã dừng và container tạm đã xóa; dịch vụ dùng chung giữ nguyên.

Nguồn, audio local, hồ sơ rà và biên nhận cũ ở `.artifacts/learning-path-a1/`;
lượt duyệt xuất bản ở `.artifacts/learning-path-a1-approval/` (gitignore).
Không đưa tài khoản, password hash, session hoặc signed URL vào file chia sẻ.
