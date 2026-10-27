## Purpose

Cho phép làm lại item hoặc topic đã hoàn thành trong phiên riêng, tiếp tục được sau khi thoát, giữ kết quả cũ đến khi từng item hoàn thành và kiểm soát thay thế lượt đang dở.

## ADDED Requirements

### Requirement: Bắt đầu và tiếp tục lượt làm lại
Hệ thống SHALL cho retry item đã hoàn thành hoặc toàn topic đã hoàn thành, kiểm tra level khi tạo lượt mới. Retry SHALL bắt đầu CONTEXT/0, không có lối tắt quiz; tiếp tục lượt đang dở SHALL không kiểm tra lại level. Mỗi user/topic SHALL có tối đa một retry session đang chạy, bất kể ITEM/TOPIC. Retry dở SHALL giữ vị trí và lần sai riêng, không đổi item hoàn thành thành IN_PROGRESS.

#### Scenario: Item đã hoàn thành có retry dở
- **WHEN** người học mở chi tiết
- **THEN** có Tiếp tục làm lại, Xem lại và Làm lại từ đầu; kết quả chính thức cũ còn hiệu lực

### Requirement: Thay retry phải xác nhận đúng lượt
Bắt đầu retry khác khi còn lượt dở SHALL yêu cầu xác nhận bỏ đúng session đã quan sát. Huỷ xác nhận SHALL không ghi. Session đã thay đổi SHALL bị từ chối thay vì ngầm bỏ session khác. Thoát ứng dụng không phải abandon.

#### Scenario: Tab khác thay session trong lúc xác nhận
- **WHEN** FE gửi xác nhận bỏ session A nhưng active session đã là B
- **THEN** trả RETRY_SESSION_CHANGED, giữ B và yêu cầu FE tải lại

### Requirement: Chốt version và thứ tự retry topic
Retry item SHALL chốt version mới nhất lúc bắt đầu. Retry topic SHALL chốt release, tập item hiển thị, version và thứ tự lúc xác nhận; item thêm sau không được nhập lượt. Item bị ẩn SHALL bị bỏ qua ngay, kể cả item đang làm, không tự hoàn thành hoặc xoá lịch sử. Bắt đầu item tiếp theo SHALL là thao tác ghi rõ ràng, không phát sinh qua GET.

#### Scenario: MAJOR xuất hiện giữa lượt
- **WHEN** release mới được publish sau khi đã chốt retry
- **THEN** item còn khả dụng tiếp tục dùng version đã chốt; cờ nội dung mới vẫn còn nếu hoàn thành ở version cũ hơn

#### Scenario: Item đang retry bị ẩn
- **WHEN** người học tiếp tục phiên sau khi item bị ẩn
- **THEN** item đó không được đọc/ghi; phiên xác định mục kế còn hiển thị hoặc kết thúc nếu không còn; GET không tự tạo attempt kế

### Requirement: Thay kết quả theo item đã hoàn thành
Retry SHALL thay kết quả hiện hành bằng lần gần nhất ngay khi từng item hoàn thành; không lấy điểm tốt nhất. Item chưa xong SHALL giữ kết quả cũ. Abandon session SHALL không rollback kết quả của item đã hoàn thành lượt mới. Topic progress SHALL không tụt chỉ vì retry; item mới được thêm vẫn làm topic về IN_PROGRESS.

#### Scenario: Kết quả mới thấp hơn
- **WHEN** item hoàn thành retry với độ chính xác thấp hơn kết quả cũ
- **THEN** kết quả mới vẫn thay kết quả cũ, các item khác giữ nguyên

#### Scenario: Retry topic bị thay sau một item
- **WHEN** item đầu đã hoàn thành retry và phiên sau đó bị abandon
- **THEN** item đầu giữ kết quả mới, các item chưa hoàn thành retry giữ kết quả cũ
