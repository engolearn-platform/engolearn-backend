## Purpose

Cho phép người học đã đăng nhập bắt đầu học nội dung chi tiết của một topic_item (Bối cảnh → Từ vựng → Mẫu câu), lưu vị trí học dở và tiếp tục đúng nơi đã dừng sau khi thoát giữa chừng.

## ADDED Requirements

### Requirement: Người học đọc toàn bộ nội dung item để học

Hệ thống SHALL trả về toàn bộ nội dung học của một topic_item (Bối cảnh, Từ vựng, Mẫu câu) khi người học yêu cầu, để đi qua bước 1–3 của luồng UC03 trước khi sang quiz.

#### Scenario: Đọc nội dung item thành công

- **WHEN** người học đã đăng nhập yêu cầu nội dung của một topic_item đang publish
- **THEN** hệ thống trả về Bối cảnh (mô tả tình huống, hội thoại mẫu nếu có), Danh sách từ vựng (chữ viết, phiên âm, nghĩa tiếng Việt, audio Anh-Anh và audio Anh-Mỹ), và Danh sách nhóm expression với mẫu câu (câu tiếng Anh, bản dịch, ghi chú cách dùng, audio)

#### Scenario: Thành phần ngoài mô tả tình huống là tuỳ chọn

- **WHEN** Bối cảnh của item không có ảnh, hội thoại mẫu hoặc audio
- **THEN** hệ thống chỉ hiển thị phần có, không báo lỗi

#### Scenario: Item không tồn tại hoặc chưa publish

- **WHEN** người học yêu cầu nội dung của một topic_item không tồn tại hoặc chưa publish
- **THEN** hệ thống trả về lỗi không tìm thấy

### Requirement: Đánh dấu item đang học dở khi bấm Tiếp tục khỏi Bối cảnh

Hệ thống SHALL chuyển item sang trạng thái IN_PROGRESS (đang học dở) khi người học bấm Tiếp tục khỏi bước Bối cảnh. Mở xem rồi thoát không tính là đã bắt đầu học.

#### Scenario: Bấm Tiếp tục khỏi Bối cảnh đánh dấu đang học dở

- **WHEN** người học bấm Tiếp tục khỏi bước Bối cảnh của một item chưa học
- **THEN** hệ thống chuyển trạng thái item thành IN_PROGRESS và lưu vị trí học dở (part = Từ vựng, index = 0)

#### Scenario: Mở xem rồi thoát không đánh dấu đang học dở

- **WHEN** người học mở xem item rồi thoát mà không bấm Tiếp tục khỏi bước Bối cảnh
- **THEN** trạng thái item vẫn là chưa học, không tạo vị trí học dở

### Requirement: Lưu và lấy vị trí học dở (part + index)

Hệ thống SHALL lưu vị trí học dở gồm part và index trong part khi người học đi qua các bước, và SHALL cho người học tiếp tục đúng vị trí đó khi quay lại.

#### Scenario: Lưu vị trí khi chuyển bước

- **WHEN** người học bấm Tiếp tục từ bước Từ vựng sang bước Mẫu câu
- **THEN** hệ thống cập nhật vị trí học dở thành part = Mẫu câu, index = nhóm mẫu câu đã xem tới

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

### Requirement: Vị trí học dở không hết hạn, nhiều topic song song

Hệ thống SHALL giữ vị trí học dở vĩnh viễn (không hết hạn) và cho phép người học học dở nhiều topic cùng lúc (mỗi topic tối đa một item đang học dở).

#### Scenario: Vị trí không hết hạn

- **WHEN** người học dừng học item tại vị trí X, sau nhiều ngày quay lại
- **THEN** hệ thống vẫn cho tiếp tục từ vị trí X

#### Scenario: Học dở nhiều topic cùng lúc

- **WHEN** người học đang học dở một item trong topic A và bắt đầu học một item trong topic B
- **THEN** hệ thống giữ riêng vị trí học dở của mỗi topic, không ảnh hưởng lẫn nhau

### Requirement: Điều kiện tiên quyết trước khi bắt đầu học item

Hệ thống SHALL chỉ cho phép bắt đầu học một item chưa học khi: người học đã đăng nhập, level topic bằng hoặc thấp hơn level hiện tại của người học, và các item đứng trước trong topic đã hoàn thành (không tính item ẩn).

#### Scenario: Đủ điều kiện bắt đầu học

- **WHEN** người học đã đăng nhập, level topic <= level người học, và item trước đã hoàn thành
- **THEN** hệ thống cho bắt đầu học item

#### Scenario: Chưa đăng nhập không bắt đầu học

- **WHEN** người học chưa đăng nhập yêu cầu bắt đầu học item
- **THEN** hệ thống từ chối, yêu cầu đăng nhập

#### Scenario: Level topic cao hơn level người học

- **WHEN** level topic cao hơn level hiện tại của người học
- **THEN** hệ thống từ chối bắt đầu học item

#### Scenario: Item trước chưa hoàn thành

- **WHEN** có ít nhất một item đứng trước trong topic chưa hoàn thành
- **THEN** hệ thống từ chối bắt đầu học item hiện tại

#### Scenario: Tiếp tục item đang dở không kiểm tra lại level

- **WHEN** người học đang học dở item (IN_PROGRESS) và tiếp tục học lại
- **THEN** hệ thống không kiểm tra lại level topic so với level người học

### Requirement: Audio không tự phát

Hệ thống SHALL không phát audio tự động; người học bấm để nghe và nghe lại không giới hạn.

#### Scenario: Audio không tự phát

- **WHEN** hệ thống hiển thị Bối cảnh có hội thoại mẫu hoặc Từ vựng có audio phát âm
- **THEN** audio không phát tự động, chỉ phát khi người học bấm nút nghe

### Requirement: Mỗi từ vựng có audio Anh-Anh và audio Anh-Mỹ

Hệ thống SHALL trả về hai trường audio cho mỗi từ vựng: `audioEn` (phát âm Anh-Anh) và `audioUs` (phát âm Anh-Mỹ), theo BR-04.6.

#### Scenario: Trả hai accent audio cho từ vựng

- **WHEN** GET nội dung item và từ vựng có bản ghi âm cả hai accent
- **THEN** phản hồi chứa `audioEn` và `audioUs` cho mỗi từ

#### Scenario: Một accent có thể thiếu

- **WHEN** GET nội dung item và từ vựng chỉ có ghi âm một accent
- **THEN** trường audio còn lại là null, không báo lỗi

### Requirement: Kiểm tra nhanh của từ vựng là tuỳ chọn

Hệ thống SHALL cho phép bỏ qua Kiểm tra nhanh của từng từ, chấm ngay khi chọn đáp án, và không tính vào độ chính xác hay hoàn thành item.

#### Scenario: Bỏ qua Kiểm tra nhanh

- **WHEN** người học không làm Kiểm tra nhanh của một từ
- **THEN** vẫn sang bước tiếp theo bình thường, không ảnh hưởng hoàn thành item

#### Scenario: Kiểm tra nhanh sai thì chọn lại

- **WHEN** người học chọn đáp án sai của Kiểm tra nhanh
- **THEN** hệ thống báo chưa đúng, cho chọn lại không giới hạn lần, không tính vào độ chính xác
