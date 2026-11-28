# Audio Cloudinary cho lộ trình cũ — 27/11/2026

Đã thực hiện theo yêu cầu chủ dự án: chuyển đúng tham chiếu audio của **300 snapshot Listening cũ** sang Media, không thay nội dung hoặc tiến độ. Nhánh: `fix/learning-path-media-with-cloudinary`. Đây là đợt riêng sau migration 30 passage ACTIVE.

## Đối chiếu và ánh xạ

Đối chiếu toàn bộ snapshot với passage RETIRED gốc: title/transcript phải khớp nguyên văn; URL phải là đường dẫn local gốc hoặc URL giả cùng chính xác tên file. Cả 300 snapshot thuộc bốn recording; upload đúng bốn file MP3 đã tồn tại, không dùng recording B1-V2 hoặc Assessment. SHA-256 file và transcript nằm trong [manifest](data/learning-path-legacy-cloudinary-mapping.json). Đây là đối chiếu dữ liệu/file, không phải xác nhận đã nghe thẩm định chất lượng chuyên môn.

| Passage gốc | Snapshot đổi | File | Media ID | Giây |
| --- | --- | --- | --- | --- |
| LP-PASS-L-ATTITUDE-B1 | 30 | listening-attitude-b1.mp3 | 4f83a319-47fe-4fdc-8938-752db1cac668 | 17.14 |
| LP-PASS-L-DETAIL-B1 | 60 | listening-detail-b1.mp3 | 5ebc6dd2-3d98-49a3-ab89-da1acc2898a3 | 23.30 |
| LP-PASS-L-GIST-B1 | 90 | listening-gist-b1.mp3 | 673e6aeb-199e-46bc-bf8e-2df60382d09b | 19.18 |
| LP-PASS-L-SPECIFIC-B1 | 120 | listening-specific-b1.mp3 | 8032a066-4684-4f6a-8f55-770993f5cb13 | 16.44 |

## Dữ liệu đã đổi và giữ nguyên

- Sao lưu BSON đầy đủ của 300 snapshot trước khi sửa; transaction kiểm tra từng bản ghi vẫn giống bản sao trước khi ghi. Chỉ `$set passage.audio_media_id` và `$unset passage.audio` ở đúng các snapshot Listening được duyệt. Không lưu URL Cloudinary tạm trong DB.
- Giữ nguyên toàn bộ trường khác của snapshot, kể cả `_id`, path/user/question ID, order, passage ID/title/transcript/revision, options, đáp án, explanation và thời gian đã lưu. Không cập nhật từ kho câu hỏi hiện tại.
- Hash dữ liệu trước/sau xác nhận collection lộ trình, kho câu hỏi và toàn bộ passage nguồn không đổi; snapshot Reading và mọi trường ngoài audio của toàn bộ 480 snapshot không đổi.
- Còn 7 lộ trình và 480 snapshot; 300/300 snapshot Listening có Media ID. Tổng Media Learning Path là 34: 30 cho bộ ACTIVE trước đó, 4 cho snapshot cũ.
- Không đổi 5 passage RETIRED sang ACTIVE hoặc thay audio của chúng; chúng vẫn giữ dữ liệu nguồn cũ. File inference không có snapshot tham chiếu nên không upload.
- API upload hiện có tạo 4 DRAFT trung gian; chúng được dọn sau khi snapshot đã tham chiếu Media và kiểm tra provider đạt. Không xóa asset hoặc metadata Media. Không còn DRAFT trung gian của đợt này.

## Kiểm chứng thực tế

- 4/4 preview đúng Media, URL chưa hết hạn; tải Cloudinary HTTP 200 và Range HTTP 206, bytes khớp đoạn tương ứng trong file tải đầy đủ.
- FFprobe xác nhận MP3 và thời lượng khớp file local (lệch dưới 0,15 giây); FFmpeg giải mã cả bốn recording không lỗi. Không tạo file âm thanh rác hoặc upload lại 30 file B1-V2.
- DB kiểm tra riêng sao chép 7 path/480 snapshot/4 Media, giữ trạng thái path/day; chỉ owner trong bản sao được gắn tài khoản demo để kiểm tra HTTP. Không đổi owner trong DB nhóm.
- Ba nhóm có ngày được phép xem lại (Gist, Specific, Detail): GET result 200, trả transcript/đáp án cũ và audio có expiry; GET audio 200/no-store; file Range 206.
- Nhóm Attitude hiện chỉ nằm trong lộ trình CANCELLED. File Cloudinary của nhóm này tải/tua/giải mã đạt, nhưng API vẫn chặn nghe qua lộ trình đã hủy với 409 theo nghiệp vụ; không tự mở lại path.
- Khách bị chặn 401, tài khoản khác bị chặn 404. Việc chuyển Media không mở rộng quyền đọc.
- Không thay Java/frontend code nên không chạy lại Maven/browser suite trong đợt dữ liệu này. Kiểm tra HTTP/Mongo/provider thật và so sánh dữ liệu là các kiểm tra sau migration.

## JSON chia sẻ

- Nguồn `tools/mongo-json-importer/samples/learning-path-demo.json`: đổi đúng 240 snapshot Listening có trong bộ demo từ URL giả sang bốn Media ID; thêm bốn metadata `media_assets`. Các collection cũ khác và mọi trường snapshot ngoài audio giữ nguyên. 300 là số snapshot local DB, 240 là số Listening snapshot trong JSON demo; không xuất bản dữ liệu người học thực từ DB.
- Theo yêu cầu chủ dự án, dùng một file chính [learning-path-demo.json](../../../tools/mongo-json-importer/samples/learning-path-demo.json). Sau khi gộp thêm bộ B1-V2, file có 5 collection/1160 bản ghi: 6 path, 390 snapshot gồm 240 Listening, 660 câu kho, 70 passage và 34 Media. Snapshot/demo cũ không thay đổi khi gộp; bộ B1 mới giữ DRAFT.
- Bốn Media của snapshot cũ nằm ngay trong file chính, không cần import metadata riêng. Không có cookie, signed URL hoặc thông tin đăng nhập trong metadata.
- Cấu hình cùng tài khoản Cloudinary chứa các recording. Không import replace cả demo lên DB đang dùng để sửa audio: phải dùng migration có precondition/transaction như đợt này. Nhập fixture không được ghi đè tiến độ thật. Các JSON pool/ACTIVE B1-V2 của đợt trước không thay đổi.

## Bản sao và khôi phục

Bản sao local ở `.artifacts/learning-path-legacy-audio-20261127/` (gitignore): `before.json`, `after.json`, `uploaded-records.json`, nhật ký upload, báo cáo delivery/review/audit và `learning-path-demo.before.json`. Không xóa thư mục này khi cần khả năng đối chiếu/khôi phục. Khôi phục phải kiểm tra bản ghi vẫn khớp `after.json`, rồi chỉ phục hồi hai trường audio theo `before.json` trong transaction; không replace đè snapshot đang thay đổi hoặc xóa Media đang có tham chiếu. Chưa chạy rollback.

Không commit/push trong đợt này. File audio local trong frontend public được giữ nguyên; dữ liệu Media và các JSON chia sẻ không chứa bí mật Cloudinary.

Sau kiểm chứng, hai backend tạm và container MongoDB kiểm thử riêng đã dừng/dọn.
MongoDB nhóm, 34 Media thực và các bản sao local được giữ nguyên.
