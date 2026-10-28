## MODIFIED Requirements

### Requirement: Người học đọc toàn bộ nội dung item để học
Hệ thống SHALL trả Bối cảnh, Từ vựng, Mẫu câu qua owned attempt theo version đã chốt và visibility item hiện hành. Thành phần ngoài mô tả tình huống SHALL là tuỳ chọn; thiếu media không chặn học. Payload MUST NOT chứa đáp án quiz chính thức. Legacy item-content/position routes đã 410 MUST NOT được dùng lại.

#### Scenario: Đọc nội dung thành công
- **WHEN** owner đọc content của attempt còn khả dụng
- **THEN** nhận đúng version đã pin với nội dung hiện có, gồm hai accent audio nếu có và các mẫu câu với bản dịch/ghi chú

#### Scenario: Thiếu thành phần tuỳ chọn
- **WHEN** không có ảnh, hội thoại hoặc audio
- **THEN** không tạo nội dung thay thế, chỉ hiển thị phần có

#### Scenario: Item không còn khả dụng
- **WHEN** item bị ẩn dù attempt đang IN_PROGRESS
- **THEN** từ chối đọc/advance/access, giữ dữ liệu lịch sử; FE về chi tiết topic

#### Scenario: Đọc nội dung item thành công

- **WHEN** owner yêu cầu content qua attempt còn khả dụng của một topic_item đang hiển thị
- **THEN** hệ thống trả về Bối cảnh (mô tả tình huống, hội thoại mẫu nếu có), Danh sách từ vựng (chữ viết, phiên âm, nghĩa tiếng Việt, audio Anh-Anh và audio Anh-Mỹ), và Danh sách nhóm expression với mẫu câu (câu tiếng Anh, bản dịch, ghi chú cách dùng, audio)

#### Scenario: Thành phần ngoài mô tả tình huống là tuỳ chọn

- **WHEN** Bối cảnh của item không có ảnh, hội thoại mẫu hoặc audio
- **THEN** hệ thống chỉ hiển thị phần có, không báo lỗi

#### Scenario: Item không tồn tại hoặc chưa publish

- **WHEN** người học yêu cầu nội dung của owned attempt không tồn tại hoặc item chưa có bản publish hợp lệ lúc start
- **THEN** hệ thống trả về lỗi không tìm thấy

### Requirement: Lưu và lấy vị trí học dở (part + index)
Hệ thống SHALL lưu stage và index 0-based theo version: CONTEXT/0, từng từ VOCAB, từng mục đích giao tiếp EXPRESSIONS, hoặc câu quiz đầu tiên chưa đúng theo dạng. Mỗi purpose hiển thị các mẫu câu cùng nhau; nav SHALL liệt kê trực tiếp purposes dưới Mẫu câu, không có tầng group. Thứ tự nhóm dữ liệu vẫn được bảo toàn khi làm phẳng, không đánh lại vị trí cũ.

#### Scenario: Chuyển mục đích giao tiếp
- **WHEN** người học Tiếp tục ở purpose hiện tại
- **THEN** lưu purpose kế theo thứ tự cố định, hoặc chuyển quiz khi hết; không cần cuộn hay nghe audio

#### Scenario: Thoát rồi tiếp tục
- **WHEN** người học trở lại attempt chưa hoàn thành
- **THEN** vào đúng vị trí đã lưu, không reset về đầu

#### Scenario: Có MAJOR mới
- **WHEN** item còn hiển thị nhưng MAJOR mới được publish
- **THEN** attempt đang dở vẫn dùng version đã pin, index không lệch

#### Scenario: Xem lại nội dung khi đang quiz
- **WHEN** người học xem phần nội dung đã mở rồi quay lại vị trí học
- **THEN** frontier vẫn là quiz hiện tại, GET không thay vị trí hoặc trạng thái

#### Scenario: Lưu vị trí khi chuyển bước

- **WHEN** người học bấm Tiếp tục từ bước Từ vựng sang bước Mẫu câu
- **THEN** hệ thống cập nhật vị trí học dở thành part = Mẫu câu, index = 0 của mục đích giao tiếp đầu tiên

#### Scenario: Tiếp tục đúng vị trí sau khi thoát

- **WHEN** người học đã lưu vị trí học dở rồi thoát ứng dụng, sau đó quay lại cùng item
- **THEN** hệ thống đưa người học đúng vị trí đã lưu (part + index), không bắt đầu từ đầu

#### Scenario: Vị trí học dở lưu theo version item

- **WHEN** Content Manager publish MAJOR version mới của item, người học đang học dở version cũ
- **THEN** người học tiếp tục học version đã chốt, vị trí học dở không bị lệch

#### Scenario: GET nội dung trả version đã chốt khi đang học dở

- **WHEN** người học đã bắt đầu học item và attempt đang chạy pin version X
- **THEN** GET nội dung trả version X, không phải version MAJOR mới nhất nếu có

#### Scenario: GET nội dung trong chế độ xem lại không đổi vị trí

- **WHEN** người học đang làm quiz hoặc xem lại item đã bắt đầu
- **THEN** GET nội dung trả nội dung đầy đủ nhưng không ghi vị trí học dở, không đổi trạng thái item

## ADDED Requirements

### Requirement: Đánh dấu item đang học dở khi bấm Bắt đầu học
Hệ thống SHALL ghi IN_PROGRESS, chốt version và lưu CONTEXT/0 khi yêu cầu Bắt đầu học thành công, trước khi hiển thị Bối cảnh. Chỉ đọc chi tiết topic SHALL không tạo tiến trình; start thất bại SHALL không đổi trạng thái.

#### Scenario: Bắt đầu rồi thoát ngay
- **WHEN** start đã thành công nhưng người học chưa rời Bối cảnh rồi thoát
- **THEN** item vẫn IN_PROGRESS, topic ở Chủ đề của tôi, lần sau tiếp tục CONTEXT/0

#### Scenario: Chỉ đọc chi tiết
- **WHEN** người học xem chi tiết topic mà không start
- **THEN** không tạo attempt hoặc tiến độ


### Requirement: Learner local dùng dữ liệu thật
Trong giai đoạn chưa có auth, hệ thống SHALL chọn hồ sơ learner đã lưu DB qua cấu hình local và giới hạn loopback. Không cấu hình SHALL là guest thật; lỗi tải hồ sơ MUST NOT trở thành guest/level giả. FE MUST NOT dùng fixture server hoặc fallback mock khi BE thiếu endpoint.

#### Scenario: Endpoint chưa có
- **WHEN** FE nhận lỗi chưa hỗ trợ hoặc không kết nối được BE
- **THEN** hiển thị trạng thái lỗi/chưa khả dụng và ghi nhận blocker, không dựng dữ liệu thành công

## REMOVED Requirements

### Requirement: Đánh dấu item đang học dở khi bấm Tiếp tục khỏi Bối cảnh
**Reason**: Quyết định nghiệp vụ BR-03.4 tính bắt đầu ngay khi start thành công, không chờ rời Bối cảnh.
**Migration**: Dùng POST start owned attempt lưu CONTEXT/0; không dùng legacy PUT position. Attempt cũ giữ ID/vị trí, không reset.
