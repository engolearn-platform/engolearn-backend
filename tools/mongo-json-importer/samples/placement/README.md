# Placement — ngân hàng 320 câu và 16 bộ đề

Gói hiện hành: `placement-demo-catalog.importer.json`, phiên bản metadata 2, theo đúng cấu trúc `{metadata, collections}` của importer chung.

| Collection | Số bản ghi | Nội dung |
| --- | ---: | --- |
| placement_questions | 320 | Ngân hàng hiện có, không sinh thêm câu hỏi |
| placement_test_sets | 16 | 7 Published, 9 Draft; tên Form 01–16 |
| media_assets | 96 | Metadata và URL Cloudinary của âm thanh |
| placement_audio_assets | 96 | Liên kết tài nguyên âm thanh Placement |

16 đề × 20 câu, mỗi ID câu hỏi xuất hiện đúng một lần giữa16 đề hiện hành. Mỗi đề gồm14 Reading/6 Listening; A1/A2 mỗi cấp4 Reading+1 Listening; B1/B2 mỗi cấp3 Reading+2 Listening. Reading đứng trước Listening. Family B2 không trùng trong một đề. Không tuyên bố đã hiệu chuẩn độ khó bằng thống kê thực nghiệm.

Chỉ7 đề Published được cấp ngẫu nhiên cho lượt làm mới.9 đề Draft vẫn chỉ ở Admin chờ duyệt; bao phủ320 câu trong danh mục không có nghĩa learner đã được cấp cả320 câu. Tiếp tục lượt cũ giữ ID/version và thứ tự đề.

## Nạp thử theo công cụ của nhóm

Gói dành cho namespace Placement trống. Không nạp cùng các bundle Placement/media cũ vì trùng ID. Không dùng replace/skip để che chênh lệch ở DB đang có dữ liệu. Kiểm tra cả hai collection media dùng chung: nếu đã có ID tương ứng, đối chiếu trước và tích hợp chênh lệch thay vì apply nguyên gói.

1. Bật MongoDB replica set, cấu hình root `.env` và `tools/mongo-json-importer/.env` theo [importer chung](../../README.md). Xác nhận DB đích và sao lưu trước.
2. Trong **file `.env` của importer**, đặt `MONGO_IMPORT_OBJECT_ID_FIELDS=none`, `MONGO_IMPORT_CONFLICT_POLICY=fail`, `MONGO_IMPORT_CONTINUE_ON_ERROR=false`. Giữ ID String và Extended JSON nguyên dạng.
3. Từ repo BE chạy:

```powershell
.\tools\mongo-json-importer\import.ps1 -File .\tools\mongo-json-importer\samples\placement\placement-demo-catalog.importer.json -DryRun
```

4. Chỉ bỏ `-DryRun` sau khi báo cáo đúng528 bản ghi mới/4 collection, không xung đột, đúng DB đích. Tool yêu cầu nhập IMPORT. Importer không phải transaction liên collection; lỗi giữa chừng phải đối chiếu trước khi chạy lại.

Luôn truyền `-File`; gói nằm trong thư mục con nên lệnh all-samples cấp trên không tự chọn nó. Không apply lại vào DB local đã được cấu hình qua API.

## Xác nhận trên ứng dụng

Chạy EngolearnApplication và `npm run dev` từ hai repo dự án. Admin Placement thấy Form01–16 với7 Published/9 Draft (có thể còn các phiên bản lịch sử). Guest bắt đầu được một đề Published; reload hoặc bắt đầu tiếp với cùng cookie phải giữ attempt/đề cũ. Một tài khoản đã có kết quả hợp lệ không tự được làm lại.

Đổi tên7 đề Published được thực hiện qua tạo phiên bản → xuất bản → lưu trữ bản cũ, giữ code và snapshot lịch sử. Local giữ riêng7 bản Archived và3 seed legacy khóa; không đưa chúng vào bundle mới vì không chứa lịch sử attempt. Một đề nháp cũ không còn tồn tại khi đồng bộ đã được thay bằng Form08 mới; không khôi phục ID đã xóa.

Gói có đáp án/transcript quản trị và URL media công khai; không có tài khoản, cookie, attempt, kết quả hay tiến độ. Media thật đã có trên Cloudinary. URI Mongo và secret cloud do nhóm cấu hình riêng. Đưa file lên Git không đồng nghĩa dữ liệu đã được ghi lên Mongo dùng chung.

## Kết quả kiểm tra

- Phân hoạch320 ID, đủ ma trận/thứ tự/family, không sinh thêm câu hỏi; liên kết96 audio_media_id đều có media_assets.
- API thật qua FE proxy:24 Guest độc lập nhận đủ7 dạng đề Published, không nhận Draft/Archived;24/24 lần bắt đầu lại và đọc lại giữ nguyên attempt/thứ tự.
- Importer thật, dry-run trên namespace Mongo biệt lập:528 bản ghi,4 collection,0 lỗi/0 trùng,0 ghi. Chưa apply gói vào DB dùng chung hoặc một DB mới.
- Kiểm thử FE/BE thật cho kết quả1 đúng/1 sai/18 bỏ trống: Sai19, không giả lập đáp án đã chọn;1/1 đạt,0 skip.

Chi tiết thiết kế và giới hạn: [báo cáo danh mục320 câu](../../../../docs/technical/placement/README.md).
