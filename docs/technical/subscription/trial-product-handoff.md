# Handoff — Thiết kế lại Trial theo Subscription Product

**Trạng thái:** Discovery / domain design review, chưa triển khai refactor.
**Repo:** engolearn-backend và engolearn-frontend, nhánh feat/ai-companion.

## Mục tiêu và quyết định đã chốt
Ý định sản phẩm: Trial là hình thức dùng thử một gói (hiện tại Premium), không phải một gói quyền lợi độc lập. Nguồn quyền lợi và quota phải là phiên bản của sản phẩm được dùng thử. Việc chọn sản phẩm đích qua cấu hình Admin thay vì hard-code là hướng đề xuất cần khảo sát thêm, nhất là khi hệ thống đã có dynamic paid products.

## Bằng chứng hiện trạng
- SubscriptionServiceImpl.activate() resolve policy("TRIAL", now), tạo period kind TRIAL trong 7 ngày và lưu snapshot. FREE/TRIAL/PREMIUM hiện có policy riêng.
- Seed trước khi sửa: Free 3600 giây/ngày, 1 assessment/tháng, 15000 AI token/ngày; Trial 3600/2/15000; Premium 7200/5/50000. Runtime đã xác minh activation HTTP 201 nhưng chỉ assessment tăng. Đây là hệ quả của policy riêng, không phải lỗi tính quota.
- API summary hiện trả tier PREMIUM, status TRIAL, currentPlanCode TRIAL, currentBenefits.planCode PREMIUM. FE validator đã được sửa để chấp nhận response này; browser E2E activation và ba quota card đã PASS.
- Biện pháp tạm thời: seed dev Trial sao chép cả ba rule và benefit text từ Premium (7200/5/50000), vẫn giữ ID/scope Trial độc lập. DB hiện hữu vẫn dùng verification-trial-v1 cũ; không ghi đè snapshot. Nếu cần đổi runtime, publish phiên bản mới qua Admin cho các kỳ mới.

## Invariants cần bảo toàn
- Trial 7 ngày, một lần/tài khoản, không thanh toán hoặc tự gia hạn; hết hạn trở về Free.
- Khi kích hoạt, pin phiên bản sản phẩm/quyền lợi tại thời điểm đó. Không đổi hồi tố quyền của kỳ đang hoạt động, kể cả khi Admin publish phiên bản mới.
- Giữ usage trong cùng cửa sổ quota khi thay đổi entitlement; không reset ledger/counter vì kích hoạt Trial.
- Trial không được coi là paid purchase thành công; quyền sở hữu nội dung lâu dài của Topic và quyền theo tài nguyên Assessment thuộc các module tương ứng.
- Giữ transaction, idempotency, outbox/receipt và public contract giữa Subscription, Quota, Payment và các consumer.

## Câu hỏi design cần phân tích trước khi thực hiện
- Tách product/product version khỏi period kind TRIAL/PAID và trial eligibility/duration. Chọn, kiểm tra và lưu product đích như thế nào khi có nhiều paid product; xử lý hidden/stopped/archived/unpublished và concurrent publication.
- Cách migrate dữ liệu legacy TRIAL policy/period, bảo toàn snapshot cũ, không cấp lại trial đã dùng; compatibility của API, admin configuration, product catalog, quota projection/event và các consumer.
- FE: phân biệt product đang dùng với trạng thái Trial; bỏ UI quản trị quota Trial độc lập nếu thiết kế mới không cần.
- Đối chiếu các business specs chính thức trước khi chốt: https://github.com/engolearn-platform/engolearn-business-docs-and-tools/tree/main/docs/usecase_spec/subscription (BL-02, BL-03, BL-10, BL-14, BL-21, BL-25, BL-28); BE docs/technical/subscription/README.md, api-contract.md, module-contract.md; FE docs/features/subscription/dynamic-products.md và docs/subscription/user-ui-handoff.md.
- Xác minh actual callers của entitlement trong Assessment, Topic, Companion, Quota, Payment. Không mặc định phải sửa Quota Engine hoặc mở rộng phạm vi.

## Acceptance dự kiến (cần chốt cùng chủ dự án)
- Trial của product X nhận đúng toàn bộ benefit/quota của version X tại activation; version mới chỉ áp dụng cho trial mới.
- Activation/replay/concurrency, expiry, Free fallback, paid purchase trong trial, giữ usage và không cấp quyền paid-only; thử các trường hợp product không khả dụng.
- Migration bảo toàn lịch sử/kỳ cũ, tính một lần và ledger; API/FE hiển thị đúng product và status, admin không còn duplicate cấu hình quyền lợi.

Ghi chú vận hành: trong lần restart local trước đó có duplicate quota entitlement projection Free v1 / Trial v2. Đã đối chiếu với period/account, giữ v2 và xóa một projection v1 lỗi thời; ledger không thay đổi. File backup dự kiến không ghi được do lỗi đường dẫn. BE đã startup lại, nhưng nguyên nhân phát sinh duplicate ban đầu chưa được kết luận. Không coi đây là yêu cầu refactor chung hoặc bằng chứng lỗi đã được phòng ngừa.

Đây là handoff để agent khác tiếp tục discovery, không phải implementation spec hay lệnh triển khai ngay. Ưu tiên hiện tại của kênh AI là quay lại AI Evaluation; tránh mở rộng scope sang Subscription redesign.
