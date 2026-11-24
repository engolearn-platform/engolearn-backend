# Placement — kết quả và User profile

`placement_attempts` lưu lượt làm, các lựa chọn, trạng thái và kết quả. Đề/snapshot được tham chiếu bằng đúng ID/version đã cấp trong `placement_test_sets`, không đổi sang bản mới nhất khi xem lại. User profile nằm ở `user_profiles.cefr_level`, không phải kết quả được ghi riêng vào collection tài khoản.

Placement gọi `UserLevelPromotion` của module User, chỉ nâng khi kết quả hợp lệ cao hơn level hiện tại; bằng thì giữ tương đương, thấp hơn không hạ. Tài khoản mới chưa đánh giá giữ mặc định của User. Không thêm collection/profile field riêng hoặc sửa trực tiếp repository của module khác.

- Lượt User: finalize/nộp hoặc xử lý deadline hợp lệ đồng bộ kết quả và promotion theo transaction.
- Lượt Guest: không có profile để cập nhật trước đăng nhập. Sau đăng nhập/đăng ký, claim hợp lệ mới gắn User và promotion; giữ ràng buộc ownership/cửa sổ claim và kết quả đã có.
- Kết quả và review yêu cầu quyền USER kế thừa (gồm CONTENT_MANAGER, ADMIN theo hierarchy nhóm), vẫn kiểm tra đúng chủ sở hữu. Admin tra cứu qua API quản trị là chỉ đọc, không được đổi điểm hoặc level.
- POST `/api/v1/placement/attempts/{attemptId}/learning` dùng kết quả của chính chủ để kích hoạt hướng học theo hợp đồng hiện tại; không cho chọn level tùy ý từ FE.

FE lấy lại User/profile sau thao tác để tránh hiển thị dữ liệu phiên cũ. [Grammar đọc profile](../grammar/grammar-placement-level-integration.md) để mở cấp, không tự nâng profile khi hoàn thành học liệu. Kiểm tra hồi quy gồm `PlacementUserLevelIntegrationTest`, ownership/claim và quyền kế thừa; cần Mongo và Auth thật.

API Profile của nhóm GET/PATCH `/api/v1/profile/me` dùng cùng `user_profiles`. Placement cập nhật riêng `cefr_level`/`updated_at`, giữ `display_name`; đổi tên hồ sơ không đổi level. `PlacementUserLevelIntegrationTest.profileApiAndPlacementUseSameUserProfileAndPreserveDisplayName` xác minh submit thật, API Profile, raw collection và Grammar roadmap cùng đọc A2; không tạo trường level ở `users`.
