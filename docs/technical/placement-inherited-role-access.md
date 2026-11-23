# Placement — inherited learner access

## Requirement / 23 November 2026
Owner approves Admin taking Placement. Team authorization is ADMIN > CONTENT_MANAGER > USER; therefore both management roles inherit learner access. In Placement specification, actor User means a capability, not an exclusive account role. Admin's management access never grants another learner's ownership. Real attempts promote the caller's own existing User profile normally; preview remains the non-assessed management tool.

## Plan and boundaries
1. Add an integration regression rendering PlacementStartView with the real AuthProvider and real cookie API through FE proxy. Observe Admin's old refusal before fix. No fabricated fetch responses.
2. Re-export existing hasRequiredRole from auth/contract (no new hierarchy or policy implementation). Add feature hook usePlacementLearnerAccess to consume that contract and current identity. Replace exclusive isUser checks throughout entry/public/attempt/result/claim/review views and layouts. Keep Guest access and existing ownership/deadline/claim semantics. Use HasRole for signed-in start content; do not modify global isUser semantics.
3. Verify full real cookie lifecycle at BE for CONTENT_MANAGER and ADMIN on an isolated Mongo test database, plus foreign ownership and Guest result authentication. Build/lint/auth and real FE regression. No BE production/security/shared shell changes, no privilege promotion of the existing demo account.
4. Update owning Placement feature docs and auth contract docs. No push/merge main/dev.

BE already grants cumulative authorities; no production source or security policy was changed. Real-cookie integration adds coverage for inherited-role submit/result/review/learning and ownership.

## Kết quả / 23-11-2026

Quy tắc: ADMIN > CONTENT_MANAGER > USER. Actor User trong use case Placement bao gồm tài khoản có ROLE_USER kế thừa. Không phải viết lại toàn bộ đặc tả; phụ lục này làm rõ cách áp dụng luật phân quyền chung. Không sửa repo đặc tả ngoài dự án.

- FE đã sửa8 điểm kiểm tra exact isUser trong entry/public/attempt/result/review/claim và khung trang. usePlacementLearnerAccess đọc hasRequiredRole qua auth/contract. Shared useAuth.isUser vẫn giữ nguyên nghĩa vai trò chính xác.
- Thay đổi dùng chung duy nhất của lượt này: auth/contract/index.ts thêm re-export hàm policy có sẵn; tài liệu auth ghi cách tiêu thụ. Không sửa thuật toán phân quyền, router đăng nhập chung, layout/sidebar/navbar, API BE hoặc business User/Grammar. Các thay đổi từ đợt trước vẫn nằm trong nhánh, không hoàn nguyên.
- Guest vẫn bắt đầu bài khi chưa đăng nhập, cần xác thực/claim mới xem kết quả. Admin/Content Manager có thể làm, tiếp tục, nhận Guest result và xem bài của chính họ theo điều kiện User hiện có. Đã có kết quả hợp lệ thì tiếp tục xem kết quả, không tự cấp quyền retake.
- Làm bài thật có thể cập nhật level hồ sơ của chính Admin/Content Manager như User, không thay đổi role. Admin muốn xem đề mà không ghi kết quả thì dùng Xem trước trong quản trị. Quản trị tra cứu toàn cục là namespace riêng; quyền kế thừa không cho sửa/claim hoặc đọc bài người khác bằng endpoint học viên.
- Test FE với AuthProvider, cookie và GET current-attempt API thật qua localhost:3000: đã thấy lỗi RED trước sửa, sau sửa1/1 đạt,0skip. Không tạo Attempt hoặc đổi level cho tài khoản Admin demo đang dùng. Dùng jsdom để kiểm tra render; không coi đây là kiểm tra ảnh bằng browser.
- BE PlacementUserLevelIntegrationTest10/10 đạt trên Mongo thật biệt lập, gồm CONTENT_MANAGER/ADMIN nộp→A2→result/review/learning, Guest401 và foreign-Admin404. Các tài khoản test role chỉ tồn tại trong DB của Testcontainers; không sửa quyền tài khoản thật.
- FE build đạt (còn cảnh báo bundle); lint0errors/72warnings hiện có; Auth21/21 đạt (suite mô phỏng của nhóm bổ sung, không thay bằng chứng API thật).
- Full BE verify875tests:12failures,49errors,32skip; Placement169tests,0failure/error,4skip; Grammar12tests,0failure/error; Architecture9/9. Danh tính61 ca lỗi ngoài hai module khớp baseline trước lượt này. Không tuyên bố toàn BE đã xanh.
- Review độc lập không phát hiện lỗi cụ thể ở quyền kế thừa/Guest/loading/ownership trong phạm vi sửa. Không push/merge main/dev, giữ nhánh BE merge/placement-guest-user-main và FE fix/placement-guest-navbar.

### Chạy lại ca FE

Với BE8080 và FE3000 thật đang chạy, truyền TEST_ADMIN_EMAIL,TEST_ADMIN_PASSWORD bằng biến môi trường riêng rồi chạy `node --test tests/placement-role.integration.test.mjs`. Có thể đặt PLACEMENT_FE_URL. Thiếu credential thì test báo skip, không xem là đạt tích hợp. Không commit giá trị credential.

Đăng nhập Admin bình thường rồi mở `/placement`; default landing sau đăng nhập của nhóm vẫn giữ nguyên. Không cần đổi tài khoản sang role USER để làm bài.
