> Cập nhật demo sau nghiệm thu: theo yêu cầu mỗi cấp1–2 chủ đề chưa xuất bản, đã Archive→Restore một chủ đề mỗi A1/A2/B1/B2 không có lịch sử học. Trạng thái hiện tại39 chủ đề/99 bài Published;4 chủ đề/7 bài Draft-ready (giao diện Đang hoàn thiện). Số43/106 Published bên dưới là thời điểm nghiệm thu trước điều chỉnh demo. Chi tiết ở FE docs/features/grammar/07-learning-management-navigation.md.

# Kết quả tích hợp Placement → User → Grammar — 23/11/2026

Đã thực hiện tại hai repo D:/TrenLop/WWWJava/Nhom/EngoLearnApp, giữ nhánh BE merge/placement-guest-user-main và FE fix/placement-guest-navbar. Không commit/push/merge main/dev trong đợt này. Chuẩn hóa cấu trúc ở đợt trước vẫn nằm trong working tree, không hoàn nguyên.

## 1. Tích hợp level

Grammar đọc user.contract.UserLevelLookup hiện có. A2 mở A1–A2, B1 mở A1–B1, B2/C1/C2 mở đến B2. Không có profile/level thì A1; lỗi provider không được biến thành quyền truy cập. Roadmap chọn tab theo level hồ sơ đã giới hạn trong bốn cấp Grammar. Mở trực tiếp chủ đề/bài cũng xét level hồ sơ; thứ tự bài học vẫn được kiểm tra ở BE.

Mở khóa được ghi trong transaction của Grammar với reason PLACEMENT, chỉ thêm, không thu hồi. Không tự hoàn thành bài và không ghi ngược level User từ Practice. Ngưỡng hoàn thành giữ 3/5. Định nghĩa MVP giữ cefr_level chỉ tăng của nhóm; đây không phải hai trường latest-result và lifetime-maximum riêng. Thiết kế và quyết định được ghi tại [thiết kế tích hợp](grammar-placement-level-integration.md); cần đồng bộ quyết định này vào tài liệu đặc tả chung khi nhóm duyệt, không tự sửa repo tài liệu bên ngoài dự án.

### Source và tài liệu tác động trong đợt nối level

- grammar/service/impl/GrammarLearnerServiceImpl.java: đọc contract, ánh xạ level, mở khóa và kiểm tra truy cập.
- grammar/model/enums/GrammarUnlockReason.java: thêm PLACEMENT.
- placement/service/PlacementUserLevelIntegrationTest.java: thêm hai ca HTTP/Mongo thật về level Grammar, deep link, không tự hoàn thành và C1/C2.
- docs/architecture/cross-feature-communication.md: đăng ký consumer Grammar → UserLevelLookup.
- Grammar README/runtime và FE docs/features/grammar/00-grammar-learning.md, 06-placement-level-integration.md: cập nhật hành vi hiện tại. Không thêm folder sản phẩm mới; dùng cấu trúc hiện có.
- Không sửa production source User, Topic, Placement, security hoặc shared layouts trong đợt nối level này. FE đã dùng currentLevel/unlocked từ API nên không cần sửa giao diện. Những thay đổi shared/router/DTO từ các đợt trước vẫn cần review riêng khi tích hợp nhánh.

## 2. Học liệu

Đã đọc toàn bộ 43 chủ đề / 106 bài / 468 khối / 530 câu và đối chiếu live API với seed: khớp trước thao tác, checklist 106 bài đều đạt. Rà A1/A2: 54 bài,240 khối,270 câu; B1/B2:52 bài,228 khối,260 câu.

Sửa tối thiểu 6 chỗ thuộc 5 bài: T0007-L002 hai prompt yêu cầu rõ present simple; T0030-L002 thay distractor to sending bằng to sent để chỉ còn một đáp án; T0039-L002 chuẩn hóa công thức either/neither; T0022-L002 và T0033-L001 yêu cầu rõ vị trí not trước to. Giữ ID, đáp án đúng và phạm vi kiến thức. Bộ seed chuẩn đồng bộ nội dung, vẫn DRAFT để môi trường mới duyệt/xuất bản qua ứng dụng; không nhúng tiến độ hoặc tài khoản kiểm thử vào seed.

Xuất bản qua Admin API thật với checklist và version CAS: 42 chủ đề/100 bài mới, giữ chủ đề A1 và 6 bài đã xuất bản trước đó. DB local hiện 43 chủ đề/106 bài PUBLISHED. Không import-replace DB, không thay seed sang PUBLISHED hoặc đánh dấu người học đã hoàn thành. Live snapshot trước/sau và nhật ký lifecycle được lưu riêng ngoài Git.

Nguồn đã kiểm tra cho các điểm sửa:
- [British Council — Present continuous](https://learnenglish.britishcouncil.org/free-resources/grammar/english-grammar-reference/present-continuous): thói quen tạm thời có thể dùng tiếp diễn, nên prompt cần nêu thì mục tiêu.
- [Cambridge Essential English Dictionary](https://assets.cambridge.org/97805211/70925/excerpt/9780521170925_excerpt.pdf): admit to + -ing hợp lệ, không dùng làm distractor sai.
- [British Council — Quantifiers](https://learnenglish.britishcouncil.org/free-resources/grammar/english-grammar-reference/quantifiers): either/neither.
- [British Council — To-infinitives, phản hồi giảng viên](https://learnenglish.britishcouncil.org/comment/159541): negative infinitive có biến thể, cần nêu mẫu đang luyện.

## 3. Nghiệm thu

- TDD: hai ca mới thất bại đúng trước sửa (A2 deep link 403, advanced profile roadmap còn A1); sau sửa nhóm tích hợp/Grammar/Architecture 30/30 đạt, không skip.
- FE proxy localhost:3000 → BE hiện đang chạy → Mongo thật: 82 kiểm tra đạt. Gồm Guest claim A1→A2, User submit A1→A2, 6 URL Cloudinary, A2 deep link trước roadmap, đủ43 chủ đề đã xuất bản, mở khóa không tạo completion, 5/5, đạt3/5, làm lại0/5 giữ completed/bestScore3, gửi trùng, ownership,401/403,login/refresh/logout. Hai tài khoản kiểm thử riêng được tạo qua đăng ký thật; không chỉnh tiến độ tài khoản cá nhân.
- FE build đạt (còn cảnh báo bundle), lint0 errors/72 warnings hiện có, Auth21/21 đạt.
- BE full verify trên DB tạm:874 tests,12 failures,49 errors,32 skipped. Grammar12 tests không failure/error; Placement168 tests không failure/error,4skip; Architecture9/9 đạt. Danh tính61 ca lỗi ngoài hai module khớp lượt baseline ngay trước tích hợp; không giảm/xóa test để làm xanh. Toàn BE chưa đạt verify.
- Review độc lập source/data không tìm lỗi cụ thể; không thay bằng chứng runtime bằng review tĩnh.
- Đợt này kiểm tra runtime qua FE proxy ở dev; không chạy lại HTTPS/prod smoke hoặc toàn bộ hình ảnh UI. Không khẳng định deploy prod hay máy thành viên đã được cập nhật.

## Test tại máy

Docker Mongo replica set hoạt động; Run EngolearnApplication với duy nhất dev, FE npm run dev. Đăng nhập tài khoản đã có kết quả A2, mở /grammar: tabA2 và cấpA1/A2 được mở, B1/B2 theo tiến độ. A1 vẫn có thể học nội dung nền. Không import lại DB local. Máy thành viên cần nhận source/seed mới, cấu hình môi trường và import đúng quy trình; merge code không tự mang theo DB hoặc publication state local.
