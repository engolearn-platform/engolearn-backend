# Nạp lộ trình Grammar A1–B2

Phạm vi được duyệt: thêm 31 chủ đề còn lại theo `grammar-cefr-roadmap.md`, giữ nguyên nội dung và ID của bộ mẫu Thì hiện tại đơn. Mỗi chủ đề mới có 2 bài, mỗi bài 4 khối lý thuyết và 5 câu (3 trắc nghiệm, 2 điền chỗ trống). Tỷ lệ loại câu là quyết định biên soạn, không phải điều kiện nghiệp vụ.

## Trình tự thực hiện

1. Đối chiếu nguồn chính thức; tự viết giải thích tiếng Việt, ví dụ/câu hỏi tiếng Anh; rà đáp án và phạm vi đã dạy.
2. Chạy DTO validation, `GrammarChecklistValidator` và `GrammarAnswerNormalizer` thật của BE trên toàn bộ 62 bài mới. Kiểm tra mã tham chiếu, trùng câu/đáp án và quan hệ bài–chủ đề.
3. Sao lưu các collection Grammar hiện tại vào `target/grammar-curriculum-load/before-load.ejson`. Đây là sao lưu dev tạm, không đưa dữ liệu người dùng vào bộ seed.
4. Nạp tuần tự A1 → A2 → B1 → B2 qua API Admin thật từ FE proxy. API cấp mã, root ID và cập nhật counter; manifest ghi ID để tiếp tục không tạo bản trùng.
5. Đọc lại tất cả chủ đề/bài qua API; kiểm tra 14/14 điều kiện bài, thứ tự theo level, giữ mẫu A1 và trạng thái Nháp. Không tự đánh dấu Sẵn sàng hoặc Xuất bản.
6. Xuất bộ seed chuẩn chung `{metadata, collections}` vào thư mục samples/grammar hiện có: `grammar_topics`, `grammar_lessons`, `grammar_lesson_progress: []`. Ghi riêng nguồn, giới hạn xếp CEFR và hướng dẫn import/counter.

## Giới hạn

Không sửa UI, model, API hoặc module khác. Không tạo tiến độ giả. Các tham chiếu điều kiện học trước chỉ là chỉ dẫn sư phạm trong tài liệu, không thêm khóa học vào DB. Các cấu trúc dùng nguồn C1 ở cuối B2 là phần giới thiệu hẹp theo roadmap nội bộ, không khẳng định CEFR bắt buộc. Dữ liệu Nháp chỉ xem được ở Admin; học viên cần quy trình duyệt và xuất bản hiện có.
