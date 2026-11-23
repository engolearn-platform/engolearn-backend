# Grammar Model V2 — thiết kế chuyển đổi

> CẬP NHẬT: chủ dự án đã duyệt tiếp bước tích hợp V2. Implementation và kiểm chứng hiện hành xem docs/technical/grammar-v2-runtime.md và grammar-v2-importer.md; DB đang dùng chưa được chuyển đổi.

## Mục tiêu và quyết định

Tiếp tục phương án chủ dự án đã chọn: lấy `D:/TrenLop/WWWJava/Nhom/data/data_1.json` làm nền thiết kế, bổ sung để đáp ứng GRM-UC-001..013, và tách Topic/Lesson thành hai collection. Giữ giao diện và HTTP DTO đang dùng. Bản thiết kế này làm cụ thể các lựa chọn còn thiếu của bản đánh giá trước, cần chủ dự án duyệt các lựa chọn trước khi thay persistence.

Phạm vi: chỉ Grammar. Không sửa cấu trúc users, Placement, Subscription, Topic từ vựng, gamification, notification hoặc user_progress_summary. Không import, migrate, tạo user hoặc ghi dữ liệu người học ở lượt chuẩn bị. Không push/merge main. File `data_1.json` gốc được giữ nguyên; bản đầy đủ ở `data_1.grammar-v2.proposed.json`.

## Các hướng đã cân nhắc

1. Giữ toàn bộ Lesson nhúng: ít thay đổi nhất, nhưng không đáp ứng hướng tách collection đã chọn.
2. Tách collection và giữ biểu diễn nội dung/API đang chạy: hướng được đề xuất. Cần repository, mapping và migration riêng; hạn chế thay FE và dữ liệu phụ trợ.
3. Chuyển đồng thời UUID sang ObjectId, đáp án sang option object và tất cả tên collection phụ trợ: chi phí và rủi ro chuyển đổi lớn hơn; không có nghiệp vụ đã xác nhận buộc phải thay đồng thời.

Lựa chọn đề xuất cần duyệt: hướng 2. UUID/String, options dạng chuỗi + correct_index và tên các collection phụ trợ hiện tại được giữ để bảo toàn liên kết. Đây là điều chỉnh có chủ đích so với data_1, không phải tuyên bố model cũ đã khớp hoàn toàn.

## Dữ liệu

- `grammar_topics`: thông tin chủ đề, code, level, title_vi/title_en, description, order/status, version chống ghi đè, bộ đếm Lesson, thời điểm xuất bản, dữ liệu khôi phục, `schema_version=2`. Không chứa nội dung Lesson ở dạng mới.
- `grammar_lessons`: `_id` là chính ID Lesson hiện có; `topic_id` liên kết Topic. Có code/order/status/version/ready/content_version, dữ liệu khôi phục, `draft` và `published`.
- Draft/published có title_vi/title_en/objective/tags/blocks/questions. Dùng đúng năm loại khối và hai loại câu đang hỗ trợ; giữ các trường cụ thể thay vì payload Object không ràng buộc. Thứ tự khối/câu là vị trí trong mảng.
- Giữ `grammar_published_revisions` bất biến, `grammar_practice_sessions`, `grammar_lesson_progress`, `grammar_level_unlocks`, `grammar_counters`, `grammar_learner_locks`, `grammar_audit_log`. Không sửa collection tổng hợp của module khác.
- `schema_version`, `version`, `content_version` có ba mục đích khác nhau. Không nhập nhằng trạng thái DRAFT của Topic với nhãn IN_PROGRESS tính từ các bài đã Sẵn sàng.
- Dùng MongoDB Date cho Instant; ID hiện có giữ nguyên cả giá trị và kiểu BSON. Không đổi ObjectId/String đại trà ở users hay các module khác.
- Topic nháp được phép chưa có Lesson; Lesson nháp được phép thiếu khối/câu. Giới hạn tối đa vẫn là 10 bài, 7 khối và 5 câu. Các mốc tối thiểu 1 bài/1 khối/đúng 5 câu chỉ bắt buộc ở bước Sẵn sàng hoặc xuất bản tương ứng; không chặn luồng tạo nháp hiện tại.
- `total_lessons` và `is_complete` của thiết kế gốc được tính từ dữ liệu và checklist; không tạo thêm bộ đếm ghi tay. `lesson_sequence` là số cấp mã, không phải số bài hoạt động. `difficulty` chưa có contract/luật hiện hành nên chưa lưu trong đề xuất V2; cần duyệt sự khác biệt này cùng lựa chọn ID/đáp án.

## Đọc, ghi và tương thích dữ liệu đang dùng

Repository tiếp tục trả aggregate `GrammarTopic` chứa danh sách Lesson cho business/mapper; đây là mô hình làm việc, không buộc phải là hình dạng lưu MongoDB. Root persistence mới của Topic/Lesson phải tuân theo BaseDocument và @Field của repo.

Chọn storage theo từng Topic: thiếu schema_version hoặc phiên bản 1 là legacy nhúng; phiên bản 2 đọc Lesson riêng. Legacy đọc/ghi theo đường cũ cho đến khi người dùng chủ động chạy migration. Topic mới dùng V2. Không tự chuyển cấu trúc khi khởi động hoặc khi người học mở bài; không đọc đồng thời hai bản rồi tự chọn một bản khi xung đột.

Tìm kiếm, lọc Nháp/Đang hoàn thiện và thống kê phải xét Lesson từ đúng nguồn của từng Topic. Dùng Mongo aggregation/join và projection phía repository, không tải tất cả bài và phân trang bằng Java. Không gửi đáp án nháp hoặc đáp án chưa nộp qua learner DTO.

Mọi thay đổi V2 trên Topic và Lesson tham gia cùng transaction MongoDB replica set. Topic version/fence vẫn là điểm đồng bộ giữa tác vụ Admin và Learner. Sắp xếp, thêm/xóa, giới hạn 10 bài, xuất bản cả Topic, ghi revision và audit phải rollback cùng nhau. Không chỉ thêm @Document vào lớp Lesson rồi để các query embedded hiện tại chạy tiếp.

Invariant cụ thể: mỗi lần sửa, thêm hoặc xóa child V2 đều CAS/ghi parent trong cùng transaction; fence của Learner kiểm tra parent và Lesson còn PUBLISHED rồi ghi cùng parent đó. Retry phải đọc lại aggregate và kiểm tra lại edit token từ caller; không retry một aggregate đã cũ. Hai lần sửa hai bài khác nhau vẫn được bảo vệ bởi parent token, không chỉ token riêng của child.

`saveTopic` chỉ nhận aggregate đã tải đầy đủ child, đối chiếu parent version, upsert các child hiện có và xóa đúng child đã bị loại khỏi aggregate trong cùng transaction. Một kết quả projection hoặc danh sách chưa hydrate không được hiểu là yêu cầu xóa toàn bộ child. Xóa cứng Topic nháp được phép phải xóa cả child V2 trong transaction, có kiểm tra version; không được để orphan. Các query dùng projection chỉ phục vụ đọc, không dùng làm đầu vào save.

## Migration chủ động

Công cụ ở `tools/mongo-json-importer/` hoặc tiểu thư mục Grammar dưới samples theo cấu trúc tool hiện có. Mặc định preflight chỉ đọc; apply là thao tác riêng chưa được thực hiện ở lượt này.

Preflight xác nhận Topic tồn tại, phiên bản và fingerprint nguồn, ID/code Lesson duy nhất, dãy order liên tục, mọi revision/progress tham chiếu cùng ID, counter không thấp hơn mã đã cấp. Nếu đã có Lesson V2 cùng ID nhưng nội dung khác, dừng; không replace. Không xử lý các collection ngoài Grammar.

Nguồn migration trong kế hoạch này chỉ là cấu trúc nhúng UUID của BE hiện tại, không phải mọi DB có tên collection tương tự. Từ chối nguồn đã có Lesson standalone/ObjectId theo thiết kế khác nếu chưa có mapping riêng được duyệt. Từ điển data_1 không chứng minh dữ liệu kiểu đó đã tồn tại.

Apply sau này: trong một transaction cho Topic, đối chiếu lại version/fingerprint, chép nguyên Lesson với ID và draft/published/version/content_version được bảo toàn, đánh dấu schema_version=2 rồi bỏ mảng lessons của Topic. Nếu thiếu hoặc lệch bất kỳ bản ghi, rollback. Migration chạy lại nhận trạng thái đã chuyển và kiểm tra khớp, không tạo bản trùng. Không đặt lại tiến độ, session hoặc audit. Trước apply cần bản sao lưu và cửa sổ không có thao tác biên soạn.

Checksum chuẩn hóa theo key và giữ thứ tự array, gồm trường Topic nguồn và toàn bộ Lesson nhúng; không gồm timestamp được tạo cho root mới. Lưu migration_source_hash và migrated_at ở parent. Lesson nhúng không có audit timestamp riêng: created_at/updated_at của root Lesson mới là thời điểm chèn root khi migrate, không giả lập ngày tạo Lesson lịch sử. Các thời điểm first_published_at giữ nguyên. Topic V2 đã được chỉnh sửa hợp lệ sau migration được báo “đã chuyển, có chỉnh sửa sau chuyển”; không so với nguồn cũ rồi tự replace.

## Mẫu A1 và giới hạn kiểm chứng

Mẫu đầu chỉ một Topic Thì hiện tại đơn, dự kiến ba Lesson: khẳng định, phủ định, câu hỏi/trả lời ngắn. Mỗi bài đúng 5 câu; đề xuất 3 MCQ + 2 FIB, không xem tỷ lệ này là luật. Dữ liệu mẫu DRAFT, ready=false, content_version=0, không có published/revision giả. Bật Sẵn sàng và xuất bản bằng API thật sau khi người dùng duyệt và cho phép import.

Counter/mã không được cấp tùy tiện offline trên DB đang có Grammar. Bundle đầu có thể dành cho DB dev Grammar rỗng; trước khi nạp vào DB đang dùng phải có bước đặt mã/vị trí theo trạng thái thật hoặc dùng luồng Admin. Không dùng replace để ép nạp.

Không gọi kiểm tra tĩnh là kiểm thử tích hợp. Lượt chuẩn bị chỉ kiểm tra file, mapping/checklist không kết nối DB. Khi được phép ghi DB tạm, cần chạy FE proxy → BE thật trên database riêng, với chính nội dung mẫu được duyệt; không dùng mocked API hoặc fixture làm bằng chứng luồng tích hợp.

## Tiêu chí nghiệm thu thay persistence

- Đọc được Topic legacy, không tự migrate; Topic V2 mới không lưu nội dung Lesson nhúng.
- Cùng API trả đủ danh sách/chi tiết, bộ lọc và thống kê cho cả hai loại Topic.
- Chống ghi đè, transaction rollback, thứ tự và giới hạn 10 bài còn đúng khi ghi nhiều document.
- Hai biên tập viên sửa hai Lesson khác nhau và trường hợp xóa đối đầu với sửa phải được kiểm tra; save từ projection phải bị chặn và không xóa child.
- Learner mở bản đã xuất bản và chấm theo revision đã ghim; cập nhật/lưu trữ đồng thời không làm sai điểm/tiến độ.
- Migration mặc định chỉ đọc; xung đột dừng; apply bảo toàn ID, phiên bản, nội dung và liên kết; chạy lại không nhân đôi.
- Mẫu A1 qua mapping, 14 mục checklist mỗi Lesson, rồi checklist Topic sau khi bật ready thật. FE/BE hoàn thiện chỉ được chốt sau kiểm tra tích hợp thật.

## Nguồn

`GRAMMAR-DATA-MODEL-REVIEW.md` và `GRAMMAR-CEFR-ROADMAP.md` trong bộ bàn giao ngày 20/11/2026. Đặc tả ở repo `engolearn-business-docs-and-tools/docs/usecase_spec/grammar-learning/`; implementation ở `engolearn-backend/src/main/java/fit/iuh/engolearn/grammar/`; hướng dẫn persistence ở `engolearn-backend/docs/architecture/implementation-conventions.md`.
