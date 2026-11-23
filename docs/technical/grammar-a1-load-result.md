# Kết quả nạp mẫu Grammar A1 — 20/11/2026

> Cập nhật 20/11/2026: đã nạp đủ lộ trình 32 chủ đề/65 bài/325 câu A1–B2 vào DB dev, tất cả DRAFT. Bộ chuẩn và hướng dẫn hiện tại ở [kết quả nạp toàn bộ](grammar-curriculum-load-result.md) và [README dữ liệu](../../tools/mongo-json-importer/samples/grammar/README.md). Các số liệu/phạm vi một mẫu A1 bên dưới là hồ sơ giai đoạn trước, không dùng để import chồng.


Đã nạp vào DB dev đang dùng `engolearn_placement_dev_v3` và xác nhận trực tiếp trên Admin ở localhost:3000. Đây là một Topic A1, không phải toàn bộ học liệu A1–B2.

- Chủ đề: Thì hiện tại đơn / Present Simple, mã ENG-GRM-T0001.
- Giữ nguyên Topic ID `0fe8ce08-ddec-4e2f-bff5-74883627be5f` và ID bài nháp đầu `235a1bf7-60b1-44e8-b01e-2b2126c6b8e1`.
- Chuyển đúng Topic nháp này từ legacy sang V2 trong transaction, sau đó điền nội dung qua Admin API để giữ quy tắc version, thứ tự và cấp mã của ứng dụng.
- Có 3 Lesson, 15 khối, 15 câu; mỗi bài đạt 14/14 kiểm tra. UI xác nhận 3/10 bài, 15/15 câu.
- Trạng thái DRAFT, readyCount=0, chưa xuất bản. Học viên chỉ thấy sau khi Admin duyệt, bật Sẵn sàng và xuất bản.
- Không tạo chủ đề trùng tên/mã, không thay counter của module khác, không tạo tài khoản/tiến độ giả. Không sửa source BE/FE hoặc push Git trong lượt nạp này.

Nguồn học liệu là gói JSON V2 theo mẫu chung. Không import file nguyên trạng chồng lên Topic nháp cũ vì khác ID và trùng code T0001. Hai bài mới được tạo bằng API nên có ID riêng của DB này; ID khối/câu và nội dung được giữ theo mẫu. Mapping đã lưu trong `target/grammar-a1-load/applied-lesson-map.json`.

Sao lưu trước nạp: `target/grammar-a1-load/before-import.json`. Bản chụp sau nạp theo envelope collections: `target/grammar-a1-load/after-import.snapshot.json`; kết quả: `target/grammar-a1-load/result.json`. Đây là hồ sơ local trong target, không đưa vào importer hoặc Git; Maven clean có thể xóa chúng nên cần giữ bản sao nếu muốn lưu lâu dài.

Xem ba bài: http://localhost:3000/admin/grammar/0fe8ce08-ddec-4e2f-bff5-74883627be5f/lessons

A2/B1/B2 và các Topic A1 còn lại mới có bảng lộ trình, chưa có bộ học liệu hoàn chỉnh và chưa được nạp. Bước sau là chủ dự án duyệt nội dung mẫu A1 rồi mới yêu cầu sinh phần còn lại.
