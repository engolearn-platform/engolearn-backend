# Placement — danh mục bao phủ ngân hàng 320 câu

## Yêu cầu và thiết kế

Ngày 23-11-2026: chủ nhiệm yêu cầu bổ sung đề để bao phủ toàn bộ 320 câu sẵn có và chuẩn hóa tên đề. Không sinh câu hỏi mới, không đổi ma trận hoặc thuật toán chấm điểm.

- 16 đề biên tập × 20 câu, mỗi ID ngân hàng xuất hiện đúng một lần trong 16 đề hiện hành.
- Mỗi đề: Reading A1=4,A2=4,B1=3,B2=3; Listening A1=1,A2=1,B1=2,B2=2. Reading đứng trước Listening. Không trùng semantic family B2 trong cùng đề.
- Giữ 7 đề Published đã duyệt; 9 đề Draft (3 cũ,6 bổ sung) chưa được cấp ngẫu nhiên. Bao phủ 320 câu trên danh mục không có nghĩa cả320 câu được cấp cho learner ngay.
- Một câu bị trùng giữa đề Nháp cũ và đề Published được thay ở Nháp bằng câu chưa dùng cùng skill/level; không sửa câu trong đề Published.
- Tên chuẩn: English Placement Test — Form NN (Bài kiểm tra trình độ tiếng Anh — Đề NN),01–16. Trạng thái hiển thị riêng.
- Đề Published không cho sửa trực tiếp. Đổi tên qua API tạo phiên bản → cập nhật → xuất bản phiên bản mới → lưu trữ phiên bản cũ. Giữ code, nội dung câu và tham chiếu phiên bản của attempt cũ; không xóa lịch sử. Ba seed legacy khóa giữ nguyên.

## Kế hoạch và kiểm tra

1. Đọc ngân hàng, ma trận production PlacementTestSetSnapshotFactory và API Admin.
2. Lập phân hoạch320 ID, kiểm tra đủ20, đúng ma trận, thứ tự và family; thực hiện toàn bộ kiểm tra trước khi ghi.
3. Dùng API thật qua FE proxy để kích hoạt câu có sẵn, lưu Nháp và đổi tên Published theo lifecycle của nhóm. Không ghi trực tiếp collection để bỏ qua validation.
4. Đọc lại16 đề, đối chiếu320 ID và trạng thái; giữ snapshot cũ; xuất lại bundle dùng chung theo importer nhóm, không kèm tài khoản/kết quả/tiến độ.
5. Dry-run importer trên Mongo biệt lập và kiểm tra real HTTP: chỉ Published được cấp, lần bắt đầu tiếp theo của cùng Guest trả lại attempt/đề cũ.

## Phạm vi chia sẻ

Bundle: tools/mongo-json-importer/samples/placement/placement-demo-catalog.importer.json. Chỉ dành namespace Placement trống và không nạp cùng bundle trùng ID. Không tự đưa dữ liệu local vào Mongo dùng chung. Audio sử dụng media_assets/Cloudinary hiện có. Không thay User/Grammar/Auth hoặc cấu trúc collection của nhóm.


## Kết quả thực hiện

- 16 đề hiện hành:7 Published/9 Draft;320 ID khác nhau, không thêm câu hỏi. Mỗi đề đúng ma trận và không trùng B2 family. Tất cả320 câu ngân hàng đã ACTIVE để chọn trong Admin; ACTIVE không tự làm9 đề Draft thành Published.
- Tên Form01–16.7 bản Published cũ chuyển Archived sau khi phiên bản mới xuất bản thành công; snapshot cũ vẫn đọc được và không đổi. Ba seed legacy giữ nguyên. Một draft gốc không còn trong danh sách/API404 khi xử lý nên Form08 được tạo bằng ID mới, không phục hồi bản ghi đã xóa.
- Gói chia sẻ có528 root documents/4 collection.24 Guest thật qua localhost:3000 nhận đủ7 dạng Published,0 Draft/Archived;24/24 resume giữ attempt và câu hỏi. Không lộ correctOptionId/explanation trong payload làm bài.
- Dry-run importer chung trên namespace Mongo biệt lập:528 mới,0 có sẵn,0 lỗi,0 ghi. Đây là kiểm tra envelope/BSON/ID; không thay cho domain validation hoặc apply thực tế. Domain matrix/thứ tự/family được kiểm tra riêng và7 đề Published đi qua validation API thực tế.
- Kiểm thử result/review FE thật1/1 đạt,0skip sau đổi tên:1 đúng/1 sai/18 bỏ trống hiển thị Sai19, vẫn không gán đáp án cho câu trống.
- Các lượt kiểm tra tạo24 attempt Guest tạm và một tài khoản test chuyên biệt cho result/review ở DB local. Không sửa kết quả/level của tài khoản cá nhân. Không xóa lịch sử để làm đẹp danh sách.
- Lượt này chỉ cập nhật data qua Admin API, gói importer và tài liệu. Không đổi production code, schema, UI, cấu hình phân quyền hoặc nghiệp vụ module khác. Chưa commit/push, chưa merge vào main/dev, chưa nạp Mongo dùng chung. Không suy thành toàn bộ ứng dụng không còn lỗi.
