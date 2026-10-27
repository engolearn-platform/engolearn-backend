## MODIFIED Requirements

### Requirement: Danh sách chỉ chứa topic đã phát hành
Hệ thống SHALL chỉ liệt kê topic có release hiện hành PUBLISHED. Guest SHALL thấy mọi topic published; learner SHALL thấy ở Khám phá chỉ topic chưa từng bắt đầu. Topic đã bắt đầu hoặc hoàn thành SHALL thuộc Chủ đề của tôi, không xuất hiện đồng thời ở Khám phá.

#### Scenario: Guest duyệt catalog
- **WHEN** không có learner được chọn
- **THEN** trả topic published, không có trạng thái hoặc tiến độ cá nhân

#### Scenario: Start thành công
- **WHEN** learner bắt đầu item đầu tiên, kể cả chưa rời CONTEXT/0
- **THEN** topic rời Khám phá và vào Chủ đề của tôi, số item hoàn thành vẫn bằng 0

#### Scenario: Chưa publish hoặc danh sách rỗng
- **WHEN** không có topic phù hợp đang published
- **THEN** trả danh sách rỗng; không tiết lộ topic chưa published

#### Scenario: Topic đã phát hành xuất hiện trong danh sách
- **WHEN** người học yêu cầu danh sách topic
- **THEN** guest nhận tập published; learner nhận tập published chưa từng bắt đầu, trước phân trang

#### Scenario: Topic chưa phát hành bị loại
- **WHEN** một topic tồn tại nhưng chưa có bản phát hành nào
- **THEN** topic đó không xuất hiện trong kết quả

#### Scenario: Chưa có topic nào được phát hành
- **WHEN** không có topic nào đã phát hành
- **THEN** hệ thống trả về danh sách rỗng, không phải lỗi

### Requirement: Sắp xếp danh sách topic
Khám phá SHALL mặc định Gợi ý: đúng level learner, level thấp hơn gần nhất trước, rồi level cao hơn thấp nhất trước; guest theo level tăng. Cùng level theo order_index và ID. Thời lượng ngắn nhất SHALL tính trên item hiển thị, tie-break bằng Gợi ý. Chủ đề của tôi SHALL theo lần truy cập gần nhất.

#### Scenario: Hai topic bằng thời lượng
- **WHEN** thời lượng bằng nhau
- **THEN** thứ tự Gợi ý và ID cho kết quả ổn định

#### Scenario: Guest không có level
- **WHEN** guest yêu cầu thứ tự mặc định
- **THEN** không tự gán level; sắp level tăng rồi order_index và ID

#### Scenario: Sắp xếp mặc định
- **WHEN** người học không chọn cách sắp xếp
- **THEN** kết quả được sắp theo Gợi ý dựa trên level learner (guest theo level tăng), rồi order_index và ID

#### Scenario: Sắp xếp theo thời lượng ngắn nhất
- **WHEN** người học chọn sắp xếp theo thời lượng
- **THEN** kết quả được sắp theo tổng thời lượng của các item đang hiển thị, tăng dần

#### Scenario: Thứ tự xác định khi khoá sắp xếp trùng nhau
- **WHEN** hai topic có cùng `order_index` hoặc cùng thời lượng
- **THEN** thứ tự giữa chúng vẫn xác định và giống nhau ở mọi lần gọi

### Requirement: Tóm tắt item trong chi tiết topic
Mỗi item SHALL có tên/mô tả/thời lượng và số từ/mẫu câu/câu quiz thật, không chứa nội dung học đầy đủ. Learner SHALL nhận trạng thái, cờ nội dung mới và quyền start/continue/review/retry từ dữ liệu hiện hành; guest SHALL không có tiến độ cá nhân. Số mẫu câu là số câu, không phải số group/purpose.

#### Scenario: Learner xem detail sau hoàn thành
- **WHEN** item đã hoàn thành
- **THEN** detail trả completed result reference và actions đúng quyền; retry dở không thay trạng thái thành IN_PROGRESS

#### Scenario: Tóm tắt không lộ nội dung
- **WHEN** bất kỳ người gọi nào xem detail
- **THEN** không trả bối cảnh/từ vựng/mẫu câu/đáp án quiz đầy đủ trong outline

#### Scenario: Đếm số mẫu câu
- **WHEN** item có nhiều group và purpose
- **THEN** đếm tổng mẫu câu, không đếm cấp chứa

#### Scenario: Tóm tắt đủ số liệu
- **WHEN** hệ thống dựng tóm tắt cho một item đang hiển thị
- **THEN** tóm tắt gồm tên tình huống, mô tả ngắn, thời lượng, số từ vựng, số mẫu câu và số bài tập

#### Scenario: Không lộ nội dung học
- **WHEN** người học xem chi tiết topic
- **THEN** phản hồi không chứa bối cảnh, từ vựng, mẫu câu hay quiz của item

#### Scenario: Không lộ tiến độ
- **WHEN** guest xem chi tiết topic
- **THEN** guest không nhận trạng thái học hay dấu cá nhân; learner nhận dữ liệu cá nhân đúng hồ sơ

### Requirement: Không có topic tiên quyết
Hệ thống MUST NOT khoá topic vì chưa xong topic khác. Topic cao level SHALL vẫn inspect được outline; chỉ start/retry mới kiểm tra level. Tiếp tục/review SHALL không kiểm tra lại level nhưng vẫn kiểm tra visibility.

#### Scenario: Topic cao level
- **WHEN** learner có level thấp hơn topic
- **THEN** vẫn đọc detail, start/retry mới không được phép

#### Scenario: Danh sách không bị khoá
- **WHEN** người học yêu cầu danh sách topic
- **THEN** topic không bị loại vì topic tiên quyết; vẫn áp dụng published/personal filters và level chỉ hạn chế start/retry

### Requirement: Truy cập chỉ đọc, không yêu cầu xác thực
Catalog public SHALL đọc được với guest; cùng hồ sơ và bộ lọc SHALL cho kết quả ổn định. Kết quả SHALL được cá nhân hoá khi có learner thật. GET MUST NOT tạo attempt, thay kết quả hoặc cập nhật lần truy cập. Mutation ghi access là thao tác riêng sau khi mở thành công nội dung.

#### Scenario: Hai learner khác nhau
- **WHEN** hai learner có lịch sử khác nhau gửi cùng query
- **THEN** kết quả có thể khác theo lịch sử, không dùng chung cache cá nhân

#### Scenario: Chỉ đọc
- **WHEN** GET catalog/detail/mine/progress được gọi
- **THEN** không ghi tiến độ hoặc kết quả

#### Scenario: Không cần xác thực
- **WHEN** một người gọi gửi yêu cầu mà không kèm thông tin đăng nhập
- **THEN** hệ thống vẫn trả về kết quả bình thường

#### Scenario: Cùng kết quả cho mọi người gọi
- **WHEN** hai guest hoặc cùng learner với cùng lịch sử gửi một yêu cầu giống nhau
- **THEN** cả hai nhận cùng một kết quả

### Requirement: Phân trang dạng cuộn cho danh sách topic
Danh sách SHALL dùng cursor, limit mặc định 20, từ 1 đến 100. Cursor SHALL gắn với learner/scope/filter/sort; cursor sai SHALL bị từ chối. Filter cá nhân SHALL áp dụng trước phân trang và total SHALL là số topic sau filter. Hết trang SHALL không có cursor kế.

#### Scenario: Cuộn không trùng
- **WHEN** lấy trang kế với cùng learner và filter, dữ liệu không đổi
- **THEN** không trùng hoặc bỏ sót topic, total thống nhất

#### Scenario: Cursor hoặc limit sai
- **WHEN** cursor không giải mã được/khác scope hoặc limit ngoài 1–100
- **THEN** từ chối yêu cầu, không chuyển sang dữ liệu giả

#### Scenario: Mặc định 20 topic mỗi trang
- **WHEN** người học yêu cầu danh sách không kèm `limit` và có nhiều hơn 20 topic đã phát hành
- **THEN** phản hồi gồm 20 topic và một con trỏ cho trang kế tiếp

#### Scenario: Yêu cầu số topic mỗi trang
- **WHEN** người học yêu cầu danh sách với `limit` bằng 5
- **THEN** phản hồi gồm tối đa 5 topic

#### Scenario: `limit` ngoài khoảng hợp lệ
- **WHEN** người gọi gửi `limit` nhỏ hơn 1 hoặc lớn hơn 100
- **THEN** hệ thống trả về lỗi yêu cầu không hợp lệ

#### Scenario: Cuộn sang trang kế tiếp không trùng không sót
- **WHEN** người học dùng con trỏ để lấy trang kế tiếp với cùng bộ lọc và cách sắp xếp
- **THEN** không topic nào bị lặp lại giữa hai trang và không topic nào bị bỏ sót

#### Scenario: Ít hơn một trang
- **WHEN** số topic đã phát hành ít hơn hoặc bằng `limit`
- **THEN** phản hồi gồm tất cả topic và không kèm con trỏ

#### Scenario: Con trỏ không hợp lệ
- **WHEN** người gọi gửi một con trỏ không giải mã được
- **THEN** hệ thống trả về lỗi yêu cầu không hợp lệ

## ADDED Requirements

### Requirement: Chủ đề của tôi và thay đổi kết quả hiện hành
Chủ đề của tôi SHALL có hai tab IN_PROGRESS/COMPLETED và số lượng từ cùng dữ liệu thật. Mẫu số tiến độ và accuracy SHALL chỉ tính item đang hiển thị. MAJOR giữ hoàn thành; item thêm làm topic về IN_PROGRESS; hidden item không chặn item sau. Lịch sử đã bắt đầu SHALL không bị xoá khi item ẩn.

#### Scenario: Retry còn dở
- **WHEN** learner retry một item đã hoàn thành
- **THEN** số hoàn thành và tab topic không đổi chỉ vì retry; thay điểm khi item xong lượt mới

#### Scenario: Item đang học bị ẩn
- **WHEN** item bị ẩn
- **THEN** không xuất hiện trong detail/progress/counts, không giữ khoá item kế và không làm topic quay lại Khám phá chỉ vì lịch sử học bị loại khỏi số đếm
