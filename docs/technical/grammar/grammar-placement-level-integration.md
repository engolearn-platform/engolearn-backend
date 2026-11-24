# Grammar và level hồ sơ

User sở hữu `user_profiles.cefr_level` và public contract `UserLevelLookup` / `UserLevelPromotion`. [Placement](../placement/placement-profile-level-integration.md) chỉ nâng level khi chấm/nhận kết quả hợp lệ; Grammar chỉ đọc qua contract, không truy cập repository User hoặc cập nhật profile riêng.

MVP giữ level hồ sơ chỉ tăng theo quy tắc nhóm. Không gọi `cefr_level` là kết quả gần nhất và không thêm trường latest/max riêng. Grammar dùng profile để mở tới cấp tương ứng; thiếu level dùng A1, C1/C2 giới hạn B2. Lỗi provider phải trả lỗi, không suy đoán cấp để mở quyền.

Roadmap ghi unlock mặc định A1 rồi các cấp từ profile với reason PLACEMENT trong lock/transaction của learner; tiếp đó đánh giá mở cấp theo hoàn thành70%. Mở khóa là hợp, không thu hồi unlock cũ. Deep link Topic/theory/practice kiểm tra profile tương tự ngay cả trước lần mở roadmap đầu; thứ tự bài trong Topic vẫn áp dụng. Có level cao không tự hoàn thành bài.

Đạt3/5 hoàn thành bài; đủ mọi bài Published hoàn thành Topic; đủ `ceil(70 * P / 100)` Topic đủ điều kiện ở cấp trước để mở cấp sau, P>0. Grammar không tự nâng profile hoặc thay thế đánh giá định kỳ. Xem [runtime](grammar-v2-runtime.md).
