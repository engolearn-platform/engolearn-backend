## Purpose

Cho phép người học khám phá nội dung học theo chủ đề: duyệt danh sách topic đã phát hành theo từng trang, và xem chi tiết một topic gồm các đơn vị học đang hiển thị, để chọn nội dung muốn học.

## ADDED Requirements

### Requirement: Danh sách chỉ chứa topic đã phát hành

Hệ thống SHALL chỉ trả về những topic đã có bản phát hành hiện hành đang ở trạng thái đã phát hành. Topic chưa từng được phát hành MUST NOT xuất hiện trong kết quả.

#### Scenario: Topic đã phát hành xuất hiện trong danh sách
- **WHEN** người học yêu cầu danh sách topic
- **THEN** mọi topic đã phát hành đều có mặt trong kết quả

#### Scenario: Topic chưa phát hành bị loại
- **WHEN** một topic tồn tại nhưng chưa có bản phát hành nào
- **THEN** topic đó không xuất hiện trong kết quả

#### Scenario: Chưa có topic nào được phát hành
- **WHEN** không có topic nào đã phát hành
- **THEN** hệ thống trả về danh sách rỗng, không phải lỗi

### Requirement: Lọc theo level và tìm theo tên

Hệ thống SHALL giới hạn danh sách theo đúng một level CEFR khi người gọi cung cấp level, và SHALL tìm theo tên topic khi người gọi cung cấp từ khoá. Khi không cung cấp level, hệ thống SHALL trả về topic ở mọi level. Tìm kiếm MUST khớp một phần tên và MUST không phân biệt hoa thường, áp dụng cho cả tên tiếng Anh và tên tiếng Việt.

#### Scenario: Lọc theo một level
- **WHEN** người học yêu cầu danh sách với một level cụ thể
- **THEN** kết quả chỉ gồm các topic thuộc đúng level đó

#### Scenario: Không giới hạn level
- **WHEN** người học yêu cầu danh sách không kèm level
- **THEN** kết quả gồm topic đã phát hành ở mọi level

#### Scenario: Tìm theo tên khớp một phần
- **WHEN** người học tìm với một từ khoá là một phần của tên topic
- **THEN** kết quả gồm các topic có tên tiếng Anh hoặc tên tiếng Việt chứa từ khoá, không phân biệt hoa thường

#### Scenario: Không có kết quả phù hợp
- **WHEN** từ khoá hoặc bộ lọc không khớp topic nào
- **THEN** hệ thống trả về danh sách rỗng, không phải lỗi

#### Scenario: Level không hợp lệ bị từ chối
- **WHEN** người gọi gửi một giá trị level không thuộc tập CEFR
- **THEN** hệ thống trả về lỗi yêu cầu không hợp lệ và không trả về danh sách

### Requirement: Sắp xếp danh sách topic

Hệ thống SHALL sắp xếp danh sách theo thứ tự gợi ý mặc định, và SHALL cho phép sắp xếp theo tổng thời lượng tăng dần. Thứ tự gợi ý mặc định SHALL là `order_index` tăng dần. Thứ tự MUST xác định: khi khoá sắp xếp của hai topic bằng nhau, thứ tự giữa chúng MUST được chốt thêm bằng định danh topic và MUST NOT phụ thuộc thứ tự trả về tuỳ ý của kho dữ liệu.

#### Scenario: Sắp xếp mặc định
- **WHEN** người học không chọn cách sắp xếp
- **THEN** kết quả được sắp theo `order_index` tăng dần

#### Scenario: Sắp xếp theo thời lượng ngắn nhất
- **WHEN** người học chọn sắp xếp theo thời lượng
- **THEN** kết quả được sắp theo tổng thời lượng của các item đang hiển thị, tăng dần

#### Scenario: Thứ tự xác định khi khoá sắp xếp trùng nhau
- **WHEN** hai topic có cùng `order_index` hoặc cùng thời lượng
- **THEN** thứ tự giữa chúng vẫn xác định và giống nhau ở mọi lần gọi

### Requirement: Số liệu tổng hợp trên thẻ topic

Mỗi topic trong danh sách SHALL kèm ảnh, tên, level, số từ vựng, số bài tập và thời lượng ước tính. Số từ vựng và số bài tập MUST là giá trị hệ thống đếm từ các item đang hiển thị của topic. Thời lượng ước tính MUST bằng tổng thời lượng của các item đang hiển thị, MUST do hệ thống bảo trì, và MUST NOT cho người dùng nhập trực tiếp. Mô tả của topic MUST NOT xuất hiện trên thẻ ở danh sách.

#### Scenario: Đếm từ vựng và bài tập
- **WHEN** hệ thống dựng thẻ cho một topic
- **THEN** số từ vựng bằng tổng số từ vựng của các item đang hiển thị, và số bài tập bằng tổng số quiz của các item đang hiển thị

#### Scenario: Thời lượng khớp tổng các item đang hiển thị
- **WHEN** hệ thống dựng thẻ cho một topic
- **THEN** thời lượng bằng tổng thời lượng của các item đang hiển thị

#### Scenario: Item bị ẩn không được tính
- **WHEN** một topic có item bị ẩn
- **THEN** item đó không xuất hiện trong danh sách và không đóng góp vào số từ vựng, số bài tập hay thời lượng

#### Scenario: Không lộ mô tả ở danh sách
- **WHEN** người học xem danh sách topic
- **THEN** thẻ topic không chứa mô tả của topic

### Requirement: Xem chi tiết topic đã phát hành

Hệ thống SHALL trả về thông tin của một topic đã phát hành gồm tên, level và mô tả. Yêu cầu chi tiết cho topic không tồn tại hoặc chưa được phát hành MUST bị từ chối như một topic không tồn tại.

#### Scenario: Xem chi tiết thành công
- **WHEN** người học yêu cầu chi tiết một topic đã phát hành
- **THEN** hệ thống trả về tên, level và mô tả của topic cùng danh sách item đang hiển thị

#### Scenario: Topic không tồn tại
- **WHEN** người học yêu cầu chi tiết một topic không tồn tại
- **THEN** hệ thống trả về lỗi không tìm thấy

#### Scenario: Topic chưa phát hành
- **WHEN** người học yêu cầu chi tiết một topic chưa từng được phát hành
- **THEN** hệ thống trả về lỗi không tìm thấy, không tiết lộ nội dung của topic

### Requirement: Danh sách item theo thứ tự cố định

Chi tiết topic SHALL chứa các item đang hiển thị của bản phát hành hiện hành, theo thứ tự cố định của topic. Item bị ẩn MUST NOT xuất hiện. Thứ tự MUST được lấy từ bản phát hành, không phải từ thứ tự của định danh item.

#### Scenario: Trả đúng tập item của bản phát hành hiện hành
- **WHEN** người học xem chi tiết một topic đã phát hành
- **THEN** hệ thống trả về đúng các item thuộc bản phát hành hiện hành của topic

#### Scenario: Giữ thứ tự cố định
- **WHEN** hệ thống trả về danh sách item
- **THEN** thứ tự item đúng như thứ tự đã chốt trong bản phát hành

#### Scenario: Item bị ẩn không xuất hiện
- **WHEN** một item của bản phát hành đang ở trạng thái bị ẩn
- **THEN** item đó không xuất hiện trong danh sách item

### Requirement: Tóm tắt item trong chi tiết topic

Mỗi item trong chi tiết topic SHALL chỉ chứa tên tình huống, mô tả ngắn, thời lượng ước tính, số từ vựng, số mẫu câu và số bài tập. Tóm tắt MUST NOT chứa nội dung học đầy đủ. Mọi người học SHALL nhận cùng mức tóm tắt. Trạng thái học của người học và dấu "có nội dung mới" MUST NOT có trong phản hồi của capability này.

#### Scenario: Tóm tắt đủ số liệu
- **WHEN** hệ thống dựng tóm tắt cho một item đang hiển thị
- **THEN** tóm tắt gồm tên tình huống, mô tả ngắn, thời lượng, số từ vựng, số mẫu câu và số bài tập

#### Scenario: Đếm số mẫu câu
- **WHEN** hệ thống đếm mẫu câu của một item
- **THEN** số mẫu câu bằng tổng số mẫu câu trong các nhóm mẫu câu của item

#### Scenario: Không lộ nội dung học
- **WHEN** người học xem chi tiết topic
- **THEN** phản hồi không chứa bối cảnh, từ vựng, mẫu câu hay quiz của item

#### Scenario: Không lộ tiến độ
- **WHEN** người học xem chi tiết topic
- **THEN** phản hồi không chứa trạng thái học hay dấu "có nội dung mới"

### Requirement: Không có topic tiên quyết

Danh sách và chi tiết topic MUST NOT khoá topic nào vì lý do chưa hoàn thành topic khác. Mọi topic đã phát hành SHALL hiển thị được cho mọi người gọi.

#### Scenario: Danh sách không bị khoá
- **WHEN** người học yêu cầu danh sách topic
- **THEN** mọi topic đã phát hành đều được trả về, không topic nào bị đánh dấu khoá hay bị bỏ khỏi danh sách

### Requirement: Truy cập chỉ đọc, không yêu cầu xác thực

Capability này SHALL chỉ cung cấp đường đọc và MUST NOT yêu cầu xác thực hay thay đổi dữ liệu. Người gọi khác nhau SHALL nhận cùng kết quả cho cùng một yêu cầu.

#### Scenario: Không cần xác thực
- **WHEN** một người gọi gửi yêu cầu mà không kèm thông tin đăng nhập
- **THEN** hệ thống vẫn trả về kết quả bình thường

#### Scenario: Chỉ đọc
- **WHEN** capability này được dùng
- **THEN** không có thay đổi nào được ghi vào kho dữ liệu

#### Scenario: Cùng kết quả cho mọi người gọi
- **WHEN** hai người gọi khác nhau cùng gửi một yêu cầu giống nhau
- **THEN** cả hai nhận cùng một kết quả

### Requirement: Phân trang dạng cuộn cho danh sách topic

Danh sách topic SHALL được trả theo từng trang với **mặc định 20 topic mỗi trang**. Người gọi SHALL yêu cầu được số topic mỗi trang qua tham số `limit`; giá trị nhỏ hơn 1 hoặc lớn hơn 100 MUST bị từ chối là lỗi yêu cầu không hợp lệ. Phản hồi SHALL kèm con trỏ cho trang kế tiếp và dấu cho biết còn trang hay không; khi không còn topic nào phía sau, phản hồi MUST NOT kèm con trỏ. Con trỏ MUST chỉ dùng được với đúng bộ lọc và cách sắp xếp đã sinh ra nó; con trỏ không giải mã được MUST bị từ chối là lỗi yêu cầu không hợp lệ. Hệ thống MUST NOT cần tính tổng số topic để trả một trang.

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
