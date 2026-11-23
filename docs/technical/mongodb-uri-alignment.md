# Đồng bộ MongoDB URI theo cấu hình nhóm

Yêu cầu: application.yaml dùng spring.mongodb.uri=${MONGODB_URI} và database=${APP_DB:engolearn_db}. File .env local chứa MONGODB_URI với kết nối đang hoạt động, không dùng nguyên URI mẫu hoặc đổi sang database khác.

Kế hoạch: cập nhật test profile trước với URI kiểm thử riêng và assertion không in URI; quan sát RED. Thay đúng khối Mongo của application.yaml, đồng bộ RuntimeProfileInitializer và application-prod.yaml từ origin/dev để không còn yêu cầu host/user/password rời. Dev vẫn kế thừa URI từ base. Thêm biến mẫu không chứa secret thật vào .env.example. Chuyển URI ghi đè cũ ở .env sang MONGODB_URI và xóa khóa ghi đè trùng; giữ APP_DB và mọi cấu hình dịch vụ khác. Kiểm tra profile, khởi động class app cổng tạm, ping Mongo/read API thật và full verify bằng database tạm. Không sửa FE, không chuyển DB chung, không seed/import/publish data hoặc push/merge main/dev.


## Đã cập nhật
- src/main/resources/application.yaml: đúng spring.mongodb.uri=${MONGODB_URI}, database=${APP_DB:engolearn_db}.
- application-dev.yaml không khai báo DB riêng, kế thừa base. application-prod.yaml và RuntimeProfileInitializer.java lấy đúng source hiện hành origin/dev cho phần profile; prod yêu cầu URI/APP_DB/CORS và cookie an toàn, không yêu cầu host/replica/user/password rời.
- .env local: MONGODB_URI giữ URI local đã hoạt động, cổng27019/database engolearn_placement_dev_v3. Bỏ khóa spring.mongodb.uri ghi đè trực tiếp; APP_DB và các secret/provider khác giữ nguyên. Không lưu URI thật vào .env.example, Git hoặc báo cáo này. .env.example chỉ thêm mẫu công khai theo yêu cầu.
- RuntimeProfilesTest: thêm4 trường hợp dev/prod thiếuURI và không cần field rời; kiểm tra giá trị bằng boolean để không in URI vào lỗi assertion.

## Kiểm tra
-19/19 kiểm tra RuntimeProfilesTest đạt.
- Chạy mới EngolearnApplication từ target/classes, profile dev cổng tạm8082: startup/login/read API đạt,43 chủ đề/106 bài Grammar,39 chủ đềPublished và5 bộ đề Placement. Đã dừng tiến trình kiểm tra riêng, không dừng app của người dùng.
- Full verify trên DB tạm dùng MONGODB_URI, không dùng URI DB nghiệp vụ:880tests,12failures,49errors,32skips. Grammar13 và Placement169 khôngfailure/error;4 Placement skip.61 ca lỗi ngoài hai module không có danh tính mới so với baseline. Toàn BE vẫn chưa đạt verify.
- Profile unit/context checks của nhóm có mock phụ thuộc kỹ thuật và vẫn có thể import file .env qua YAML; các giá trị được kiểm tra có override test riêng. Không xem suite này là chứng nhận Mongo hoạt động. Bằng chứng Mongo thật là startup/API trên và các integration tests dùng Testcontainers; không dùng mock API để kết luận tích hợp.
- Review độc lập không tìm lỗi runtime wiring; phát hiện bảng biến môi trường cũ trong runtime-configuration.md và đã sửa cùng lượt.

Không sửa FE, không chuyển dữ liệu lên DB chung, không import/reset/publish hoặc đổi data, không merge full dev/main và không commit/push. Việc này chỉ đồng bộ Mongo URI/profile liên quan; các cập nhật dev khác vẫn chờ tích hợp riêng.
