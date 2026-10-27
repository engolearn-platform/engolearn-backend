## Purpose

Cho phép người học trả lời tuần tự hai dạng quiz trên phiên bản đã chốt, nhận giải thích sau khi đúng và tiếp tục chính xác sau khi thoát hoặc mất kết nối.

## ADDED Requirements

### Requirement: Chuỗi quiz cố định và không lộ đáp án
Hệ thống SHALL chỉ phục vụ quiz đầu tiên chưa đúng của owned attempt khả dụng, sau khi xong nội dung. Thứ tự SHALL là FILL_BLANK rồi SITUATIONAL_CHOICE, giữ thứ tự câu trong từng dạng. Mỗi item hợp lệ SHALL có 3–5 câu mỗi dạng; FILL_BLANK một chỗ trống, 4–6 lựa chọn; SITUATIONAL_CHOICE tình huống tiếng Việt, 4 lựa chọn tiếng Anh. Câu và lựa chọn SHALL có ID ổn định; trước khi đúng MUST NOT trả đáp án, giải thích, correctness hoặc độ chính xác.

#### Scenario: Chưa học hết nội dung
- **WHEN** người học yêu cầu quiz trước khi tới phần quiz
- **THEN** yêu cầu bị từ chối, không mở khoá hoặc trả câu hỏi tương lai

#### Scenario: Chuyển dạng
- **WHEN** mọi FILL_BLANK đã đúng và còn SITUATIONAL_CHOICE
- **THEN** câu hiện tại là câu tình huống đầu tiên, không quay lại FILL_BLANK

#### Scenario: Lựa chọn bị đổi thứ tự hiển thị
- **WHEN** câu hiện tại được hiển thị lại
- **THEN** ID câu và lựa chọn không đổi ý nghĩa chấm, không lấy chữ hoặc vị trí hiển thị làm đáp án

### Requirement: Chấm ngay và lưu lần sai
Hệ thống SHALL chấm ở BE ngay khi chọn option hợp lệ của câu hiện tại. Sai SHALL giữ câu hiện tại, lưu rằng câu không còn đúng ngay lần đầu, cho chọn lại không giới hạn và chỉ phản hồi chưa đúng. Không có bộ đếm sai trên màn học. Quick check MUST NOT tham gia kết quả chính thức.

#### Scenario: Sai rồi reload
- **WHEN** lần sai đã được BE xác nhận và người học thoát rồi quay lại
- **THEN** vẫn ở câu đó, lần chọn đúng sau này không được tính đúng ngay lần đầu

#### Scenario: Lỗi request không phải đáp án sai
- **WHEN** option không thuộc câu, request hết hạn revision hoặc mạng lỗi chưa rõ kết quả
- **THEN** không tự ghi nhận câu sai; FE khôi phục trạng thái BE hoặc gửi lại cùng định danh yêu cầu

### Requirement: Đúng là hoàn thành câu trước khi bấm Tiếp tục
Hệ thống SHALL lưu hoàn thành câu và frontier tiếp theo trước phản hồi đúng. FE SHALL giữ giải thích của câu vừa trả lời tới khi bấm Tiếp tục, không tự chuyển hoặc yêu cầu nghe audio. Thoát sau khi đúng SHALL vào câu kế hoặc tổng kết nếu là câu cuối; không yêu cầu bấm Tiếp tục để hoàn thành.

#### Scenario: Đúng rồi thoát trước Tiếp tục
- **WHEN** BE đã ghi nhận đúng nhưng người học chưa bấm Tiếp tục rồi thoát
- **THEN** lần vào sau bắt đầu ở câu kế hoặc tổng kết, không hiện lại giải thích câu cũ

#### Scenario: Giải thích theo loại
- **WHEN** câu trả lời đúng được xác nhận
- **THEN** FILL_BLANK hiển thị đáp án/câu hoàn chỉnh/giải thích và cấu trúc ngữ pháp do nội dung cung cấp; SITUATIONAL_CHOICE giải thích ngữ cảnh/lịch sự; audio nếu có chỉ phát khi bấm

### Requirement: Gửi trùng và nhiều tab không làm lệch kết quả
Mỗi mutation SHALL yêu cầu revision và Idempotency-Key. Replay cùng key/payload SHALL trả cùng kết quả nghiệp vụ mà không thêm submission hoặc tăng revision lần nữa; cùng key khác payload SHALL bị từ chối. Revision cũ với key mới SHALL bị từ chối, không được chấm thành một lần sai.

#### Scenario: Mất response của đáp án đúng cuối
- **WHEN** BE đã commit hoàn thành nhưng FE gửi lại cùng key và payload
- **THEN** trả lại kết quả đã commit, không hoàn thành hai lần hoặc thay điểm

### Requirement: Xem lại nội dung khi đang quiz
Người học SHALL xem được nội dung đã mở ở chế độ chỉ đọc và quay lại vị trí học hiện tại. Trong attempt đang chạy MUST NOT chọn lại hoặc mở lại quiz đã đúng.

#### Scenario: Đổi URL về quiz cũ
- **WHEN** người học sửa URL/nav tới quiz đã đúng trong lượt đang học
- **THEN** không hiện lại quiz đó để trả lời hoặc xem giải thích; đưa về quiz hiện tại, không đổi tiến độ

### Requirement: Item ẩn ngừng phục vụ quiz
Hệ thống SHALL kiểm tra visibility hiện hành trên đọc và gửi đáp án, kể cả khi attempt pin version cũ. Item ẩn MUST NOT nhận submission mới hoặc được tự hoàn thành do bị ẩn.

#### Scenario: Đang quiz thì bị ẩn
- **WHEN** người học gửi request sau khi item đã bị ẩn
- **THEN** BE từ chối vì item không còn khả dụng; FE dừng quiz, thông báo và về chi tiết topic; giữ lịch sử cũ
