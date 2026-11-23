# Grammar: ngưỡng mở cấp 70% — 23/11/2026

## Thiết kế được chủ dự án chọn
- Hoàn thành bài vẫn3/5; chủ đề chỉ hoàn thành khi đủ tất cả bài Published.
- Mở cấp tiếp theo khi C >= ceil(7P/10), với P là chủ đề Published có ít nhất1 bài Published ở cấp trước, C là chủ đề đã hoàn thành trong tập đó. P=0 không mở. Dùng số nguyên, không dùng percent hiển thị đã làm tròn.
- Cấp đã lưu mở giữ vĩnh viễn; không migrate/xóa unlock cũ, không hạ quyền đã có. User profile vẫn được đọc qua contract đã tích hợp; không nâng level hồ sơ do học Grammar.
- Cùng một hàm threshold dùng cho cấp quyền và thông báo còn thiếu. Không thêm kỳ thi bắt buộc, lời mời100% hoặc nối Assessment trong đợt này.
- Giữ39 chủ đềPublished/4 chủ đềdemo Draft-ready trên DB local. Không import/replace/reset tiến độ.

## Trình tự thực hiện
1. Regression HTTP/Mongo thật: nhập8 chủ đề A1 từ seed chuẩn vào Testcontainers, Ready/Publish qua Admin API, học toàn bộ bài của từng chủ đề bằng Practice3/5. Xác nhận khóa bài2 trước bài1; chưa đủ bài chưa hoàn thành chủ đề; 4/8 và5/8 chưa mở A2,6/8 mở. Sau khi archive2 chủ đề đã hoàn thành,4/6 không đủ70% nhưng A2 đã mở vẫn giữ; profile vẫn A1. Xem RED trước sửa.
2. Chỉ đổi threshold BE và cập nhật đặc tả tích hợp/tài liệu FE. Không tạo trạng thái/model/API/UI mới. Xem GREEN, chạy full verify trên DB tạm riêng và FE build/lint.
3. Review Grammar FE/BE độc lập, xử lý lỗi cụ thể trong phạm vi Grammar nếu phát hiện rồi kiểm tra lại phần bị ảnh hưởng. Báo rõ phần chưa kiểm tra được; không tuyên bố hết lỗi toàn dự án.
4. Giữ các nhánh hiện tại, không push/merge main/dev; chưa chỉnh Placement.

## Đã triển khai và review

BE GrammarLearnerServiceImpl dùng UNLOCK_TOPIC_PERCENT=70 và ceil(total*70/100) bằng số nguyên. Cùng hàm threshold được dùng ở đánh giá mở khóa và lockHint; thông báo ghi rõ70%. FE hiển thị dữ liệu API, không tính lại ngưỡng ở trình duyệt. Không thêm API/model/collection hoặc thay chấm Practice. Bản đặc tả nhóm bên ngoài repo không tự sửa; quyết định70% được ghi tại tài liệu dự án này theo yêu cầu chủ dự án.

### Bằng chứng kiểm thử
- TDD RED: API bản cũ báo cần4/8 chủ đề, trái yêu cầu6/8. GREEN: test HTTP/Mongo thật từ8 Topic và học liệu seed chuẩn, không giả phản hồi API. Ready/Publish qua Admin API; người học thực sự seen/Practice3/5 để hoàn thành từng bài. Chỉ hoàn thành1 bài trong chủ đề chưa được tính hoàn thành chủ đề; bài2 bị khóa trước bài1. 4/8 và5/8 không mở,6/8 mở. Archive2 chủ đề hoàn thành làm C/P còn4/6 nhưng cấp đã lưu vẫn mở; User profile vẫnA1.
- Full mvnw verify trên DB tạm:876tests,12failures,49errors,32skips. Grammar13tests khôngfailure/error; Placement169tests khôngfailure/error,4skips; Architecture9/9. Danh tính61 ca lỗi ngoài hai module khớp lượt baseline Placement trước đó; không có ca lỗi mới. Toàn BE vẫn chưa đạt verify.
- FE build đạt (còn cảnh báo kích thước bundle), lint0errors/72warnings hiện có. Lượt này không đổi FE source, chỉ cập nhật tài liệu.
- FE proxy localhost:3000 → BE local thật: lockHint xác nhận A1 có8 chủ đề Published, cần6; message chứa70%. DB vẫn39 Topic/99 Lesson Published,4 Topic/7 Lesson demo Draft-ready. Không reseed, không hạ unlock hoặc sửa tiến độ người học, không tạo tài khoản kiểm thử mới trong DB local.

### Review Grammar
Review độc lập đọc FE/BE: published-only; khóa tuần tự bài; pinned revision; chấm/duplicate/retry; ownership; transaction; archive/restore và completion chờ; quản trị version conflict/draft/apply/discard/Ready/autoPublish; theory/practice/result/summary/navigation/error. Không phát hiện thêm lỗi cụ thể trong phạm vi đã đọc. Kiểm thử runtime nêu trên bổ sung cho review tĩnh, không dùng mock để tuyên bố tích hợp đạt.

Giới hạn: không kiểm tra trực quan toàn bộ màn bằng browser, không chờ thật30phút cho mọi nhánh hết hạn, không chạy lại HTTPS/prod smoke trong lượt này. Assessment và gợi ý khi hoàn thành100% chưa được thêm, không gọi đó là tính năng đã triển khai. Không khẳng định toàn dự án hết lỗi.

### Phạm vi file và cách test
- BE production: grammar/service/impl/GrammarLearnerServiceImpl.java.
- BE regression: grammar/service/GrammarLearningUnlockIntegrationTest.java (HttpClient + Auth thật + Testcontainers + học liệu canonical).
- BE/FE: tài liệu Grammar current rule và báo cáo này. Không sửa Placement source, User, Assessment, shared layouts hoặc data seed trong đợt70%.
- Giữ hai nhánh hiện tại, không commit/push/merge main/dev.

Run EngolearnApplication với dev, FE npm run dev. Tài khoản chưa mở cấp tiếp theo sẽ thấy số chủ đề còn thiếu theo70%. Nếu tài khoản đã mở cấp từ50% trước đây hoặc level hồ sơ đã đủ thì vẫn được học: đây là giữ quyền đã cấp, không phải lỗi tính ngưỡng. B2 là cấp cuối nên không mở thêm cấp mới.
