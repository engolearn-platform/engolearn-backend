# Chạy Placement và Grammar

## Cấu hình của nhóm

Đọc [runtime contract](../../architecture/runtime-configuration.md), [cookie Auth](../../runbooks/user-auth.md) và [Media](../media/integration-guide.md). Chỉ chọn một profile `dev` hoặc `prod`; không dùng các profile module cũ.

BE dùng `spring.mongodb.uri: ${MONGODB_URI}` và `spring.mongodb.database: ${APP_DB:engolearn_db}` trong `application.yaml`; prod yêu cầu APP_DB rõ ràng. Đặt giá trị thực trong `.env` được Git ignore hoặc secret store. Không commit URI có mật khẩu, JWT secret hoặc Cloudinary secret. Mongo phải hỗ trợ replica set/transaction; khởi động Docker/Mongo trước BE.

## Chạy trên máy thành viên

1. Lấy cặp code BE/FE đã tích hợp. Cần JDK 21, Node/npm, Mongo replica set và cấu hình riêng của môi trường.
2. Trong BE, cấu hình `.env` theo nhóm rồi Run `EngolearnApplication` với Active profiles=`dev` trong IDE. Hoặc:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

3. Trong FE:

```powershell
npm ci
npm run dev
```

4. Mở http://localhost:3000. Vite mặc định chuyển `/api` tới BE 8080. Nếu 8080 đã được sử dụng, xác định tiến trình đang chạy; không khởi động thêm một BE trùng cổng.
5. Đăng nhập bằng tài khoản nhóm có đúng quyền. [Gói tài khoản quản trị](../../../tools/mongo-json-importer/samples/management-accounts.importer.json) có hướng dẫn seed riêng; không import/đặt lại tài khoản trên DB chung tùy tiện.

## Dữ liệu

- [Placement](../../../tools/mongo-json-importer/samples/placement/README.md): 320 câu, 16 đề (7 Published, 9 Draft), metadata 96 audio Cloudinary. Dùng đúng bundle hiện hành, không nạp chồng gói legacy.
- [Grammar](../../../tools/mongo-json-importer/samples/grammar/README.md): 43 chủ đề, 106 bài, 468 khối, 530 câu. Seed là Draft/ready=false; phải kiểm tra, Sẵn sàng và xuất bản qua Admin để học viên nhìn thấy.
- Merge code không tự nạp Mongo. Namespace đã có dữ liệu cần đối chiếu/sao lưu và tích hợp chênh lệch; không dùng replace/reset để tránh xung đột.

Dev có thể dùng WAV đóng gói ở `tools/mongo-json-importer/samples/placement-media`. Prod không có filesystem route, phải dùng media bền vững qua module Media/Cloudinary và đề Published. Prod yêu cầu HTTPS/secure cookie cùng biến môi trường triển khai; không tắt bảo vệ cookie để demo prod trên HTTP.

## Xác nhận bàn giao

- Guest: `/placement` → làm bài → đăng nhập/đăng ký → nhận kết quả và xem lại; người đã đăng nhập đi thẳng kết quả. Lượt mới nhận đề Published ngẫu nhiên, tiếp tục giữ đề đã cấp.
- Admin: `/admin/placement` → đề / ngân hàng / kết quả; `/admin/grammar` → chủ đề / bài / checklist / xuất bản. CONTENT_MANAGER chỉ có quyền Grammar tương ứng, không mặc định quản trị Placement.
- Học viên: `/grammar` → chủ đề → lý thuyết → luyện tập → kết quả/tiếp tục; chỉ thấy nội dung Published theo cấp đã mở.

Kiểm tra code bằng `.\mvnw.cmd verify` ở BE; `npm run build`, `npm run lint`, `npm run test:auth` ở FE. Kiểm thử tích hợp yêu cầu BE/Mongo thật; thiếu đầu vào hoặc bị skip không phải là đã pass. Mỗi lần bàn giao báo kết quả thực tế và lỗi còn tồn tại, không dùng báo cáo cũ thay cho kiểm tra.

Sau khi import bộ chung và chạy BE, thực hiện [shared demo setup](../../../tools/mongo-json-importer/README.md#shared-placementgrammar-demo-state) để đối chiếu7/9 bộ đề và thiết lập39/4 chủ đề Grammar. Máy đã có bộ43/106 không import lại; preflight/Apply dùng API hiện có và không reset tiến độ.
