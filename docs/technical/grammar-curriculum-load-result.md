> Số liệu hiện hành sau bổ sung23/11/2026: xem [kết quả coverage](grammar-coverage-result.md); các số liệu của đợt triển khai bên dưới là hồ sơ lịch sử.

# Kết quả nạp Grammar A1–B2

> Cập nhật 22/11/2026: chủ dự án đã cho phép xuất bản riêng mẫu Grammar A1 Thì hiện tại đơn (3 bài) để test local; 31 chủ đề khác giữ Nháp. Số liệu Nháp/khớp seed bên dưới là thời điểm nạp 20/11, trước bước xuất bản. Xem [báo cáo User v4](grammar-user-v4-result.md).


Ngày 20/11/2026. Đã nạp thật vào MongoDB dev `engolearn_placement_dev_v3` bằng API Admin qua FE Vite proxy `localhost:3000` → BE `localhost:8080`. Không thay cấu hình DB hoặc source FE/BE ở lượt nội dung này.

| Cấp | Chủ đề | Bài | Khối | Câu |
| --- | ---: | ---: | ---: | ---: |
| A1 | 8 | 17 | 71 | 85 |
| A2 | 8 | 16 | 64 | 80 |
| B1 | 8 | 16 | 64 | 80 |
| B2 | 8 | 16 | 64 | 80 |
| **Tổng** | **32** | **65** | **263** | **325** |

Đã giữ nguyên Topic `ENG-GRM-T0001` và ba Lesson của “Thì hiện tại đơn”; chỉ vị trí chủ đề trong A1 chuyển từ 1 sang 3 để phù hợp roadmap. Có 31 chủ đề/62 bài mới. Mã do API cấp; khoảng trống T0002 là lịch sử counter, không phải thiếu chủ đề trong lộ trình. Counter hiện `topic_sequence=33`, lần tạo kế tiếp cấp T0034; không ép mã theo số thứ tự hiển thị.

## Trạng thái và kiểm tra

- Toàn bộ 32 chủ đề/65 bài là **DRAFT**, `ready=false`, `content_version=0`, chưa xuất bản. Học viên chưa thấy bộ Nháp; đây là chờ duyệt, không phải lỗi FE.
- Chạy production Bean Validation, `GrammarChecklistValidator`, `GrammarAnswerNormalizer` trên cả 62 bài mới: đạt. Kiểm tra trùng câu hoàn chỉnh, phương án, biến thể đáp án sau chuẩn hóa và định danh.
- Rà chéo toàn bộ A2/B1/B2, chỉnh câu MCQ có hai cách chia đúng với danh từ tập hợp, thay mẫu động từ chưa dạy, làm rõ thứ tự phủ định và yêu cầu chọn câu đúng ngữ pháp. A1 được rà với mục tiêu bài và dữ kiện ngữ cảnh.
- Đọc lại **65/65 bài qua API thật**, mỗi bài đạt **14/14** điều kiện; mỗi cấp có 8 chủ đề, order liên tục 1–8; Lesson liên tục trong từng Topic. Nội dung và ID ba bài mẫu A1 được đối chiếu giữ nguyên.
- Bộ xuất kiểm tra 32 root Topic + 65 root Lesson, schema_version=2, liên kết topic_id đúng, ID/code không trùng, 263 khối/325 câu, không xuất tài khoản, phiên hoặc tiến độ học thật.
- Dry-run bằng **importer chung** đọc đúng 3 collection/97 document; DB hiện đã có cả 97 root nên policy `fail` dừng đúng với `writeResults=[]`, không có lỗi parse (`errors=[]`). Đây là xác nhận định dạng và phát hiện trùng trên DB hiện có, **không phải** đã thử Apply nguyên gói trên một DB trống khác.
- Không chạy lại toàn bộ kiểm thử ứng dụng vì lượt này chỉ thêm dữ liệu/tài liệu. Kết quả này không khẳng định các lỗi ngoài Grammar trước đây đã được xử lý. Chưa chạy học viên với toàn bộ nội dung mới vì chưa xuất bản; không tự bật xuất bản để tạo bằng chứng.

## Xem trên máy hiện tại

1. Bật Docker/MongoDB như cấu hình nhóm; chạy `EngolearnApplication` từ BE và `npm run dev` từ FE ở `D:/TrenLop/WWWJava/Nhom/EngoLearnApp`.
2. Đăng nhập Admin chung đang dùng, mở `http://localhost:3000/admin/grammar`, tải lại trang nếu đang mở trước khi nạp. Chọn A1/A2/B1/B2: mỗi cấp 8 chủ đề.
3. Mở chủ đề → danh sách bài → thông tin bài/khối nội dung/câu luyện tập. Mỗi bài mới 4 khối, 5 câu; ba bài mẫu A1 giữ 5 khối, 5 câu.
4. Admin đọc và duyệt nội dung, chạy Kiểm tra bài → đánh dấu Sẵn sàng. Chỉ Xuất bản khi bro chốt nội dung. Sau xuất bản mới kiểm tra `/grammar` phía học viên theo quyền level hiện có; bài tiếp theo trong chủ đề mở sau khi đạt ít nhất 3/5.

## File và cách chia sẻ

- Bộ chuẩn hiện tại: `tools/mongo-json-importer/samples/grammar/grammar-cefr-a1-b2.v2.importer.json`.
- Counter cho **namespace Grammar trống**: `grammar-cefr-a1-b2.counter-bootstrap.importer.json`. Không đè counter trên DB có dữ liệu.
- Hướng dẫn import: `tools/mongo-json-importer/samples/grammar/README.md`; nguồn và lưu ý biên soạn ở `grammar-curriculum-sources.md`.
- Sao lưu dev trước nạp và manifest có thể tiếp tục: `target/grammar-curriculum-load/`. Thư mục target bị Git ignore và Maven clean có thể xóa; đây là bằng chứng/sao lưu tạm, không phải nguồn dữ liệu bàn giao. Bộ JSON đầy đủ ở tools mới là file dùng chung cần đưa vào Git.
- Lượt này **không commit, không push, không merge vào main**. Dữ liệu đã có trong DB local; thành viên khác cần nhận các file trên nhánh rồi import đúng hướng dẫn, không nhận DB qua Git một cách tự động.

## Danh sách chủ đề đã nạp

| Tham chiếu | Mã API | Chủ đề | Số bài |
| --- | --- | --- | ---: |
| A1-01 | ENG-GRM-T0003 | Đại từ chủ ngữ và be | 2 |
| A1-02 | ENG-GRM-T0004 | Danh từ, mạo từ và sở hữu cơ bản | 2 |
| A1-03 | ENG-GRM-T0001 | Thì hiện tại đơn | 3 |
| A1-04 | ENG-GRM-T0005 | There is/are và giới từ vị trí | 2 |
| A1-05 | ENG-GRM-T0006 | Can và câu mệnh lệnh | 2 |
| A1-06 | ENG-GRM-T0007 | Hiện tại tiếp diễn | 2 |
| A1-07 | ENG-GRM-T0008 | Quá khứ đơn nhập môn | 2 |
| A1-08 | ENG-GRM-T0009 | Hỏi thông tin và nối câu cơ bản | 2 |
| A2-01 | ENG-GRM-T0010 | Kể chuyện trong quá khứ | 2 |
| A2-02 | ENG-GRM-T0011 | Hiện tại hoàn thành nhập môn | 2 |
| A2-03 | ENG-GRM-T0012 | Nói về tương lai | 2 |
| A2-04 | ENG-GRM-T0013 | So sánh và mức độ | 2 |
| A2-05 | ENG-GRM-T0014 | Số lượng và danh từ | 2 |
| A2-06 | ENG-GRM-T0015 | Lời khuyên, nghĩa vụ và yêu cầu | 2 |
| A2-07 | ENG-GRM-T0016 | Mệnh đề quan hệ nhập môn | 2 |
| A2-08 | ENG-GRM-T0017 | Điều kiện quen thuộc | 2 |
| B1-01 | ENG-GRM-T0018 | Nhìn lại quá khứ và khoảng thời gian | 2 |
| B1-02 | ENG-GRM-T0019 | Trình tự kể chuyện và thói quen cũ | 2 |
| B1-03 | ENG-GRM-T0020 | Câu bị động cơ bản | 2 |
| B1-04 | ENG-GRM-T0021 | Mở rộng mệnh đề quan hệ xác định | 2 |
| B1-05 | ENG-GRM-T0022 | Tường thuật lời nói và câu hỏi | 2 |
| B1-06 | ENG-GRM-T0023 | Điều kiện giả định hiện tại | 2 |
| B1-07 | ENG-GRM-T0024 | Khả năng và suy đoán | 2 |
| B1-08 | ENG-GRM-T0025 | Động từ theo sau bởi -ing/to | 2 |
| B2-01 | ENG-GRM-T0026 | Thời và thể trong diễn đạt chi tiết | 2 |
| B2-02 | ENG-GRM-T0027 | Điều kiện trái thực và hỗn hợp | 2 |
| B2-03 | ENG-GRM-T0028 | Bị động và cấu trúc nhờ làm | 2 |
| B2-04 | ENG-GRM-T0029 | Mệnh đề quan hệ bổ sung | 2 |
| B2-05 | ENG-GRM-T0030 | Tường thuật với động từ đa dạng | 2 |
| B2-06 | ENG-GRM-T0031 | Suy đoán và đánh giá quá khứ | 2 |
| B2-07 | ENG-GRM-T0032 | -ing/to và thay đổi nghĩa | 2 |
| B2-08 | ENG-GRM-T0033 | Liên kết ý và mệnh đề phức | 2 |

SHA-256 bộ nội dung: `97fd93a0676e3959d618e8f71d916cdc5c19c4cd7876d36121793c705ce3bcf7`.
