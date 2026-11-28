# Kho câu hỏi B2 đang sử dụng

Bộ B2 tiếp nối cách đóng gói của [bộ A2](learning-path-a2-question-bank.md).
Đã xuất bản ngày 28/11/2026 trên nhánh `feat/learning-path-question-bank`.
Phạm vi là dữ liệu kho Learning Path và Media; không sửa module Assessment,
mapping recommendation hoặc frontend.

## Nội dung và tiêu chí

- 300 câu ACTIVE: 150 Listening, 150 Reading, đúng 30 câu cho mỗi canonical micro-skill
  trong catalog; bốn lựa chọn khác nội dung, một đáp án, giải thích có bằng chứng.
- 60 passage ACTIVE khác nội dung: 30 Listening, 30 Reading, năm câu/passage. Tiêu đề
  không có prefix level. Question ID theo `LP-Q-*-B2-V1-*`; passage Listening
  dùng ID thật do API cấp.
- 30 recording mới trên Cloudinary, policy PROTECTED, tham chiếu bằng
  `audio_media_id`; không nhúng URL local hoặc signed URL có hạn vào bundle.
- Đáp án đúng được phân bố 75 A/75 B/75 C/75 D.

Áp dụng các quy tắc nội dung, bằng chứng, nhiễu, media và rà duyệt trong workspace
`AGENT.md` §17.1–17.7. Ma trận ở đó hiện mô tả chi tiết A1–B1, chưa có ma trận B2.
Hướng biên soạn B2 đối chiếu thêm [CEFR Companion Volume của Council of Europe](https://rm.coe.int/common-european-framework-of-reference-for-languages-learning-teaching/16809ea0d4):
theo dõi lập luận, phân biệt quan điểm và nhận ra quan hệ giữa các ý; dùng ngữ cảnh
để suy nghĩa. Đây là định hướng biên soạn, không phải chứng nhận CEFR.

Nguồn dài khoảng 115–133 từ, về công việc, dịch vụ công, giáo dục, cộng đồng,
tiêu dùng và môi trường. Độ khó nằm ở điều kiện/giới hạn của kết luận, bằng chứng
chưa đầy đủ, đối lập giữa mục tiêu và chỉ số, quan điểm có nhượng bộ. Không yêu
cầu kiến thức chuyên ngành ngoài bài. Câu tìm thông tin vẫn tìm dữ kiện được nói/
viết rõ; không đổi thành suy luận để tăng level. Câu từ vựng có tín hiệu ngữ cảnh.
Câu thái độ/mục đích dựa trên lập trường thể hiện bằng lời, không hỏi mỉa mai hay
sắc thái giọng tinh tế mà TTS không thể hiện được.

## Rà duyệt và audio

Codex rà nội dung, đáp án, bằng chứng, phạm vi kết luận và các lựa chọn nhiễu.
Hồ sơ nội bộ ghi mục tiêu kỹ năng, lý do gán level, phương án và nguồn liên quan.
Đây là rà nội dung bằng AI, chưa hiệu chuẩn qua người học hoặc duyệt chuyên môn
độc lập.

Audio tổng hợp bằng Microsoft Zira Desktop, Rate 0, MP3 mono 96 kbps, chuẩn hóa
âm lượng; thời lượng khoảng 44–54 giây, khoảng 141–167 từ/phút. Đây là số đo của
recording, không phải ngưỡng CEFR. Đối chiếu hash transcript/file, nhận dạng
faster-whisper, rà khác biệt, kiểm tra tín hiệu và giải mã; không tuyên bố người
thật đã nghe duyệt.

Một câu phủ định trong passage về thư viện bị cả tiny.en và base.en nhận thiếu
âm đầu. Đã đổi thành câu khẳng định tương đương, tạo lại audio và cập nhật đúng
passage DRAFT bằng API có revision. Bản mới giữ đúng ý trong cả hai lần nhận dạng.
Các khác biệt còn lại chủ yếu là chính tả Anh/Mỹ, từ gần âm hoặc từ nối; các bằng
chứng quyết định đáp án đã được đối chiếu. Bundle chỉ chứa 30 Media đang được
tham chiếu, không chứa recording bị thay thế.

## Pull và import

File chia sẻ:
[`learning-path-b2.json`](../../../tools/mongo-json-importer/samples/learning-path-b2.json).
Ba collection, 390 bản ghi: 60 `question_pool_passages`, 300 `question_pool`,
30 `media_assets`. Không cần các công cụ biên soạn nội bộ để import.
Passage/câu hỏi trong bundle là bản xuất MongoDB sau khi publish qua API, có
`publication_review` thật và revision; không tự gán ACTIVE trong JSON để bỏ qua
readiness hoặc phiếu duyệt. Publication dùng session CONTENT_MANAGER được ủy quyền.

Import catalog trước. Cấu hình tool-local `.env` trỏ đúng DB, policy `skip`,
`MONGO_IMPORT_OBJECT_ID_FIELDS=none` vì ObjectId đã dùng Extended JSON:

```powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\learning-path-b2.json -DryRun
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\learning-path-b2.json
```

Media phải dùng cấu hình Cloudinary chứa các asset đã upload. Import JSON không
tự sao chép audio sang tài khoản Cloudinary khác. Lọc B2 trong admin để xem bộ.
Đủ kho cho mười micro-skill không tự thêm mã Assessment; recommendation vẫn dùng
năm mã đã ánh xạ và các điều kiện hiện có.

## Kiểm chứng

- Kiểm tra cấu trúc, canonical/skill/level, ID, tham chiếu, bốn lựa chọn khác nhau,
  key và bằng chứng đạt cho đủ 300 câu. Không dùng lại passage A1/A2.
- Trước publish: readiness của 60 passage đạt, 300 câu chỉ có blocker dự kiến
  `PASSAGE_NOT_ACTIVE`. Publish passage trước, rồi câu hỏi; bản xuất cuối có đủ
  60 passage/300 câu ACTIVE và phiếu duyệt.
- 30/30 audio: HTTP 200, Range bytes 2000–3999 trả 206 đúng bytes, giải mã được;
  thời lượng Cloudinary lệch file local dưới 0,15 giây. Sau sửa recording,
  đối chiếu tiny.en đạt khoảng 97–100%; đã rà các sai khác, không dùng tỷ lệ này
  làm chứng nhận level hay thay thế kiểm tra nội dung.
- Dry-run file chia sẻ: 390 existing, 0 new, 0 replace, không có lỗi.
- Backend/MongoDB riêng: profile bản sao B2, source READY bản sao vẫn A1, để kiểm
  tra nguồn level của Learning Path. Recommendation AVAILABLE với năm mã hiện
  ánh xạ; tạo path B2 và tạo lại cùng source trả cùng path. Có 150 snapshot,
  60 Listening dùng Media ID, không có legacy audio URL trong snapshot.
- Start ngày trả 10 câu B2 Reading/Listening, không lộ key/explanation/transcript.
  Lưu 10 lựa chọn và resume giữ thứ tự/đáp án; audio/renewal/Range đạt. Submit
  COMPLETED và chấm khớp key; review có đúng lựa chọn, đáp án, giải thích và
  transcript. Guest đọc kết quả bị chặn 401.
- Hash toàn bộ question pool/passage A1/A2/B1 trước/sau khớp. Không tạo path hoặc
  sửa profile/source trong DB dùng chung. Backend/MongoDB kiểm thử đã dừng và
  container tạm đã xóa. Không chạy bộ Maven của module khác vì chỉ bổ sung dữ liệu.

Biên nhận, bản nguồn, audio local, ASR và báo cáo ở `.artifacts/learning-path-b2/`
(gitignore). Không đưa tài khoản kiểm thử, password hash, session hoặc signed URL
vào file chia sẻ. Chưa chạy thử nghiệm hiệu chuẩn với người học B2 thực tế.
