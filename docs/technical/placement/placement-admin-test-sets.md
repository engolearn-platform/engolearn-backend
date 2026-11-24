# Quản lý bộ đề Placement

Các API dưới tiền tố `/api/v1/placement/admin/test-sets` dùng JWT cookie của ứng dụng và yêu cầu vai trò `ADMIN`. Chế độ persistence `mongo` dùng cấu hình transaction trên MongoDB replica set hoặc sharded cluster. Các phản hồi có dữ liệu dùng `ApiResponse<T>`. Lỗi dùng `ProblemDetail` với mã Placement và chi tiết để xử lý trên màn quản trị.

| Phương thức và đường dẫn | Đầu vào | Kết quả |
| --- | --- | --- |
| `GET` tại đường dẫn gốc | `search` theo mã hoặc tên, `status`, `page` từ 0, `size` từ 1 đến 100 | Trang `{items, total, page, size}` |
| `POST` tại đường dẫn gốc | `{name}` | 201 và bản nháp V1 với mã mới |
| `GET /{id}`, `GET /{id}/preview` | ID của phiên bản | Bộ đề và nội dung xem trước |
| `PUT /{id}` | `{name, questionIds, revision}` | Bản nháp với revision mới |
| `POST /{id}/publish` | `{name, questionIds, revision}` | Phiên bản đã xuất bản với snapshot |
| `POST /{id}/versions` | ID của phiên bản gốc | 201 và bản nháp kế tiếp, cùng mã và thứ tự ID câu hỏi |
| `POST /{id}/archive` | `{revision}` | Phiên bản đã lưu trữ với snapshot ban đầu |
| `DELETE /{id}` | Query `revision` | 204 sau khi xóa bản nháp không có lượt làm tham chiếu |

Bộ đề gồm `id`, `code`, `name`, `version`, `status`, `questionIds`, `snapshot`, `createdAt`, `revision` và `lockedSeed`. Tên tối đa 160 ký tự. Danh sách trả ID câu hỏi theo thứ tự và `snapshot: []`. Xem trước bản nháp biên soạn lấy nội dung hiện tại của các câu tìm thấy trong ngân hàng. Xem trước đề đã xuất bản, đề lưu trữ hoặc bản nháp seed đọc nội dung theo thứ tự từ snapshot. Bản ghi cũ có thể thiếu thời điểm tạo và mặc định revision bằng 0.

Xuất bản cần đúng 20 ID khác nhau của câu đang `ACTIVE`. Reading nằm ở vị trí 1–14 và Listening ở vị trí 15–20. A1/A2 mỗi mức có 4 Reading và 1 Listening, B1/B2 mỗi mức có 3 Reading và 2 Listening. Backend dùng factory snapshot hiện có, kiểm tra transcript và WAV của câu Listening, sau đó ghi snapshot và trạng thái trong một MongoDB transaction. Transaction ghi dấu trên từng tài liệu câu hỏi theo trạng thái và revision đã đọc để tranh chấp với thao tác sửa hoặc đổi trạng thái. Yêu cầu dựa trên revision cũ trả 409. Vi phạm nội dung hoặc ma trận trả 422.

Mỗi cặp `code, version` và mỗi mã đang có bản nháp biên soạn đều được bảo vệ bằng unique index. Đề seed giữ nguyên ID và snapshot. Hai cặp ID/version `PLT-V3-DEV-001-V1`/1 và `PLT-V3-DEV-001-V2`/2, cùng mã `PLT-V3-DEV-001`, được đánh dấu `lockedSeed` khi ở trạng thái `DRAFT`. Quản trị viên mở phiên bản mới để dùng lại các câu trong đề seed. Fallback dành cho profile `dev` có `allow-draft=true` chỉ chọn hai bản seed này theo phiên bản mới nhất có snapshot 20 câu khi thiếu đề `PUBLISHED` đủ điều kiện. Lượt làm đã cấp tiếp tục đọc snapshot bằng ID và version, kể cả khi đề đã lưu trữ.
