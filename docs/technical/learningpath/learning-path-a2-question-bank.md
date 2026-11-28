# Kho câu hỏi A2 đang sử dụng

Bộ A2 được soạn và xuất bản ngày 28/11/2026 trên nhánh
`feat/learning-path-question-bank`, theo yêu cầu chủ dự án: rà tiêu chí trong
workspace `AGENT.md` rồi chuyển dữ liệu đạt yêu cầu sang ACTIVE.

## Dữ liệu

- **300 câu ACTIVE:** 150 Listening, 150 Reading, đúng 30 câu cho mỗi canonical
  micro-skill của catalog. Mỗi câu có bốn lựa chọn A–D, một đáp án đúng và giải
  thích tiếng Việt có bằng chứng từ passage. Vị trí đáp án đúng: 75 A/75 B/75 C/75 D.
- **60 passage ACTIVE:** 30 Listening, 30 Reading; năm câu/passage. Tiêu đề mô tả
  nội dung, không có prefix A2. ID câu hỏi theo `LP-Q-*-A2-V1-*`; passage Listening
  dùng ID do API cấp, các liên kết đã được đồng bộ.
- **30 audio Cloudinary** mới, policy PROTECTED, passage tham chiếu bằng
  `audio_media_id`. Không có signed URL hoặc URL local cố định trong bundle.

Chủ đề: thay đổi lịch, đi lại, học tập, dịch vụ, mua sắm, công việc đơn giản và
trải nghiệm sinh hoạt. Độ khó A2 thể hiện ở việc nối vài thông tin gần nhau, lý do,
trình tự, lựa chọn có điều kiện và phân biệt thông tin ban đầu với thông tin mới.
Không chỉ đổi tên/số từ A1 hoặc kéo dài câu để tăng level.

## Rà nội dung và audio

Codex đã rà đủ 60 passage/300 câu theo tiêu chí chủ dự án yêu cầu và ma trận nội bộ
trong AGENT.md: đúng micro-skill, từ vựng/cách hỏi quen thuộc ở A2, một đáp án được
nguồn hỗ trợ rõ nhất, nhiễu phản ánh nhầm chi tiết/điều kiện, explanation có bằng
chứng, không cần kiến thức chuyên môn ngoài bài. Các câu đo mục đích/thái độ dựa
vào lời xin lỗi, đề nghị, cảm ơn hoặc nhận xét rõ trong thông điệp, không dựa vào
giọng mỉa mai hoặc cảm xúc tinh tế mà giọng tổng hợp không thể hiện được.

Audio tổng hợp bằng Microsoft Zira Desktop, Rate -2, MP3 mono 96 kbps, chuẩn hóa
âm lượng. Thời lượng đo được khoảng 30–36 giây; tốc độ khoảng 121–149 từ/phút.
Các con số là mô tả recording, không phải ngưỡng CEFR bắt buộc.

Đã đối chiếu 30 file bằng faster-whisper, rà các khác biệt nhận dạng với transcript
và bằng chứng câu hỏi, kiểm tra hash đầu vào/file, tín hiệu giải mã, mức âm lượng
và clipping. Khác biệt nhận dạng gồm chính tả Anh/Mỹ, tên riêng, cách viết số và
một vài nhận dạng gần âm; các thông tin quyết định đáp án được giữ. Đây là **rà
nội dung bằng AI và kiểm tra audio tự động**, không phải tuyên bố người thật đã
nghe duyệt hoặc bộ đã được hiệu chuẩn CEFR qua người học. Nên tiếp tục điều chỉnh
theo phản hồi thực tế.

Upload qua API passage hiện có, Learning Path gọi Media; không gọi Cloudinary
trực tiếp. Sau khi kiểm tra, đã xuất bản passage trước rồi câu hỏi qua API
readiness/publish có revision và lưu `publication_review` thật bằng session
CONTENT_MANAGER được ủy quyền. Không cập nhật trực tiếp status trong MongoDB để
bỏ qua quy trình xuất bản.

## Pull và import

File chia sẻ:
[`learning-path-a2.json`](../../../tools/mongo-json-importer/samples/learning-path-a2.json).
Ba collection, **390 bản ghi**: 60 `question_pool_passages`, 300 `question_pool`,
30 `media_assets`. Tất cả passage/câu hỏi trong file đã ACTIVE và có phiếu duyệt.
Không cần các file công cụ biên soạn nội bộ.

Từ root backend, cấu hình importer đúng DB local, conflict policy `skip`,
`MONGO_IMPORT_OBJECT_ID_FIELDS=none` (ObjectId cần thiết đã dùng Extended JSON):

```powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\learning-path-a2.json -DryRun
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\learning-path-a2.json
```

Catalog micro-skill phải được import trước. Cấu hình Media phải dùng Cloudinary
đang chứa các asset; JSON không tự upload/copy audio sang một cloud khác. `skip`
giữ nguyên bản ghi có cùng ID, không dùng replace toàn bộ DB để nhập bộ này.

Trong admin, lọc level A2 để xem nội dung đang sử dụng. Đủ 30 ACTIVE/canonical/level
cho phép Learning Path áp dụng các điều kiện recommendation hiện có. Vẫn chỉ có
năm mã Assessment đã được ánh xạ; không thêm mapping hoặc sửa module Assessment.

## Kiểm chứng đã chạy

- Nguồn: 60 passage khác nội dung, 300 câu/10 micro-skill; canonical/nhóm/level,
  bốn lựa chọn khác nhau, một key, bằng chứng có trong nguồn, ID và liên kết đạt.
- Trước xuất bản: readiness 60 passage đạt; 300 câu chỉ có blocker dự kiến
  `PASSAGE_NOT_ACTIVE`. Sau xuất bản: 60 passage và 300 câu ACTIVE, phiếu duyệt
  được lưu, mỗi micro-skill đúng 30 câu của bộ A2-V1.
- 30/30 audio: Cloudinary HTTP 200, Range bytes=2000–3999 trả 206 đúng bytes;
  FFmpeg giải mã được, thời lượng lệch so với file local dưới 0,15 giây.
- Import cuối dry-run: 390 existing, 0 new, 0 replace, 0 errors.
- Backend và MongoDB kiểm thử riêng: dùng bản sao tài khoản/source READY và dữ liệu
  A2 thật. Profile bản sao đặt A2 trong khi source là A1 để kiểm tra đúng nguồn
  level luyện tập. Recommendation AVAILABLE có năm kỹ năng; tạo path A2 thành
  công; tạo lại cùng source trả cùng path, không tạo bản thứ hai.
- Path kiểm thử có 150 snapshot, 60 Listening snapshot dùng Media. Start ngày có
  10 câu Reading/Listening từ bộ A2, không lộ đáp án/explanation/transcript.
  Lưu đủ 10 đáp án và resume giữ lựa chọn/thứ tự; audio/renewal/Range đạt. Submit
  COMPLETED; chấm đúng theo key snapshot; review khớp đáp án đã lưu và có giải
  thích/transcript phù hợp. Guest đọc kết quả bị chặn 401.
- Hash toàn bộ question pool và passage A1/B1 trước/sau khớp. Không tạo path hoặc
  đổi profile/source trong DB dùng chung. Không chạy bộ Maven của module khác.

Biên nhận, nguồn nội dung, ASR, báo cáo provider/publication/learner nằm trong
`.artifacts/learning-path-a2/` (gitignore). Không đưa account, password hash,
session, dữ liệu source kiểm thử hoặc URL có thời hạn vào file chia sẻ.
