# Placement — bắt đầu và tiếp tục

API gốc `/api/v1/placement/attempts`, phản hồi `ApiResponse`, dữ liệu cá nhân `Cache-Control: no-store`. Guest dùng cookie HttpOnly `placement_guest_session`; User dùng cookie Auth chung. Không lấy userId do trình duyệt tự khai để xác định chủ sở hữu.

| Endpoint | Mục đích |
| --- | --- |
| POST gốc | Bắt đầu lượt mới hoặc trả lượt/kết quả hiện có theo điều kiện nghiệp vụ |
| GET `/current` | Dò lượt hiện có để hiển thị Tiếp tục, không tạo bài mới |
| GET `/{attemptId}` | Đọc lượt theo chủ sở hữu |
| PATCH `/{attemptId}/answers` | Lưu lựa chọn với DTO questionId/selectedOptionId |
| POST `/{attemptId}/submit` | Chấm/nộp theo quy tắc thời gian |
| GET `/{attemptId}/claim-gate` | Xác định bước Guest đăng nhập/nhận kết quả |

Lượt mới chọn ngẫu nhiên trong các đề Published đủ điều kiện; Reading/Listening và thứ tự câu giữ theo snapshot. Tiếp tục không đổi đề, không reset thời gian. GET current không tạo lượt nhưng có thể giải quyết deadline theo nghiệp vụ; không coi đây là API không có bất kỳ tác động nào.

Guest làm bài trước → màn yêu cầu đăng nhập → claim → kết quả/review. User làm xong đi thẳng kết quả. Không tự cấp lượt làm lại cho tài khoản đã có kết quả hợp lệ. Nộp/claim và cập nhật level xem [User integration](placement-profile-level-integration.md).

FE màn đang làm vẫn phân biệt câu chưa trả lời. Kết quả cuối gộp câu sai và bỏ trống vào Sai, câu bỏ trống giữ selectedOptionId=null và không giả đáp án đã chọn. Đồng hồ theo deadline BE, resume giữ thời điểm đã bắt đầu. Audio khi phát câu khác sẽ pause audio đang phát, giữ vị trí để nghe tiếp.
