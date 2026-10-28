## Purpose

Xác định hoàn thành bằng các câu quiz đã đúng, cung cấp kết quả hiện hành đáng tin cậy cho item/topic và cho phép xem lại chỉ đọc theo version đã hoàn thành.

## ADDED Requirements

### Requirement: Hoàn thành item nhất quán ở đáp án cuối
Hệ thống SHALL hoàn thành attempt khi mọi quiz trong scope đã đúng, không có ngưỡng điểm. Kết quả hiện hành, tổng kết, quyền mở item sau và trạng thái attempt SHALL cùng nhận diện một kết quả đã commit; không để người dùng thấy attempt hoàn thành nhưng kết quả mất hoặc vẫn khoá bài sau. GET MUST NOT tạo tiến trình hoặc sửa kết quả.

#### Scenario: Câu cuối đúng sau nhiều lần sai
- **WHEN** BE nhận đáp án đúng hợp lệ của câu cuối
- **THEN** item hoàn thành dù độ chính xác thấp, vị trí kết thúc và kết quả đã được lưu trước khi phản hồi thành công

#### Scenario: Lỗi giữa các ghi kết quả
- **WHEN** tiến trình lưu bị gián đoạn hoặc response bị mất
- **THEN** replay/recovery trả trạng thái chưa commit có thể tiếp tục an toàn hoặc kết quả đã commit đầy đủ; không có kết quả chính thức nửa vời

### Requirement: Tổng kết chính xác từ các số đếm
Tổng kết item SHALL hiện firstTryCorrectCount/totalQuizCount và phần trăm làm tròn một lần. Tổng kết topic SHALL cộng số đếm kết quả hiện hành của các item đang hiển thị rồi mới làm tròn, kèm danh sách từng item. Không trung bình phần trăm đã làm tròn, không số lần sai hoặc XP/huy hiệu. Topic SHALL hoàn thành khi mọi item đang hiển thị đã hoàn thành; topic rỗng không tự hoàn thành.

#### Scenario: Item cuối của topic hoàn thành
- **WHEN** mọi item hiển thị có kết quả hoàn thành
- **THEN** topic vào nhóm Đã hoàn thành, tổng kết có số đếm gộp và lựa chọn xem lại/làm lại

#### Scenario: Thêm item mới sau hoàn thành
- **WHEN** topic đã hoàn thành được publish thêm item ở cuối
- **THEN** kết quả cũ giữ nguyên, topic về Đang học tới khi xong item mới; MAJOR của item cũ không làm mất hoàn thành

### Requirement: Xem lại chỉ đọc kết quả hiện hành
Xem lại SHALL dùng version của lượt hoàn thành hiện hành, gồm nội dung và tất cả quiz với đáp án đúng/giải thích/audio/nhãn đúng ngay lần đầu. Không chọn lại, không hiển thị lịch sử lựa chọn sai, không đổi vị trí/trạng thái/kết quả. MINOR áp dụng tại chỗ, MAJOR chỉ lên khi làm lại. Kết quả đã bị retry thay thế SHALL không được trình bày như kết quả hiện hành.

#### Scenario: Đang retry dở vẫn xem kết quả cũ
- **WHEN** item đã hoàn thành đang có retry chưa xong
- **THEN** Xem lại vẫn dùng kết quả chính thức cũ, không dùng điểm tạm của retry

#### Scenario: Mở đường dẫn kết quả cũ
- **WHEN** attempt trong URL đã bị kết quả retry hoàn thành thay thế
- **THEN** trả RESULT_CHANGED và cung cấp đường quay về kết quả hiện hành; không tự ghi đè gì

#### Scenario: Item hoàn thành bị ẩn
- **WHEN** người học đọc result hoặc review của item hiện đang ẩn
- **THEN** nội dung không được phục vụ, item không đóng góp tổng kết hiện hành; lịch sử vẫn còn trong DB
